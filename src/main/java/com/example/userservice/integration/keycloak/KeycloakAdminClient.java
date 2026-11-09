package com.example.userservice.integration.keycloak;
import com.example.userservice.integration.keycloak.KeycloakProperties;
import com.example.userservice.integration.keycloak.KeycloakUser;
import com.example.userservice.integration.keycloak.IdentityException;
import com.example.userservice.modules.user.service.IdentityManagementPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
@Component
public class KeycloakAdminClient implements IdentityManagementPort {
    private static final String OWNER = "mplProvisioningId";
    private final KeycloakProperties config;
    private final HttpClient http;
    private final io.micrometer.core.instrument.MeterRegistry metrics;
    private final JsonMapper json = JsonMapper.builder().build();
    private volatile String accessToken;
    private volatile Instant expiresAt = Instant.EPOCH;
    public KeycloakAdminClient(KeycloakProperties config, @Qualifier("keycloakHttpClient") HttpClient http) {
        this(config, http, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }
    @org.springframework.beans.factory.annotation.Autowired
    public KeycloakAdminClient(KeycloakProperties config, @Qualifier("keycloakHttpClient") HttpClient http,
                               io.micrometer.core.instrument.MeterRegistry metrics) {
        this.config=config; this.http=http; this.metrics=metrics;
    }
    public String createDisabled(String email, String fullName, UUID provisioningId) {
        return createIdentity(email, fullName, provisioningId, null);
    }
    public String createDisabledWithPassword(String email, String fullName, UUID provisioningId, String password) {
        Objects.requireNonNull(password);
        return createIdentity(email, fullName, provisioningId, password);
    }
    private String createIdentity(String email, String fullName, UUID provisioningId, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", email); body.put("email", email); body.put("firstName", fullName); body.put("lastName", "");
        body.put("enabled", false); body.put("emailVerified", false);
        body.put("attributes", Map.of(OWNER, List.of(provisioningId.toString())));
        body.put("requiredActions", password == null ? List.of("UPDATE_PASSWORD") : List.of());
        if (password != null) body.put("credentials", List.of(Map.of("type", "password", "value", password, "temporary", false)));
        // The identity and its initial password are created in one Keycloak operation, never retried blindly.
        var response = call("POST", "/users", body, false);
        String location = response.headers().firstValue("Location").orElseThrow(IdentityException::unavailable);
        String id = location.substring(location.lastIndexOf('/') + 1);
        if (id.isBlank()) throw IdentityException.unavailable();
        return id;
    }
    public Optional<Identity> findById(String id) {
        var response = call("GET", "/users/" + enc(id), null, true, true);
        return response.statusCode() == 404 ? Optional.empty() : Optional.of(KeycloakUser.from(tree(response)));
    }
    public Optional<Identity> findByExactUsername(String username) { return findExact("username", username); }
    public Optional<Identity> findByExactEmail(String email) { return findExact("email", email); }
    private Optional<Identity> findExact(String field, String value) {
        JsonNode matches = tree(call("GET", "/users?" + field + "=" + enc(value) + "&exact=true&max=2", null, true));
        if (matches.size() > 1) throw new IdentityException(409, "IDENTITY_CONFLICT", "Multiple matching identities require reconciliation");
        return matches.isEmpty() ? Optional.empty() : Optional.of(KeycloakUser.from(matches.get(0)));
    }
    public void updateProfile(String id, String fullName) {
        call("PUT", "/users/" + enc(id), Map.of("firstName", fullName, "lastName", ""), true);
    }
    public void setEnabled(String id, boolean enabled) {
        call("PUT", "/users/" + enc(id), Map.of("enabled", enabled), true);
        if (!enabled) call("POST", "/users/" + enc(id) + "/logout", null, true);
    }
    public void assignRealmRole(String id, String role) { realmRole(id, role, "POST"); }
    public void removeRealmRole(String id, String role) { realmRole(id, role, "DELETE"); }
    private void realmRole(String id, String role, String method) {
        if (!config.allowedRealmRoles().contains(role)) throw new IllegalArgumentException("Realm role is not allowlisted");
        var representation = tree(call("GET", "/roles/" + enc(role), null, true));
        call(method, "/users/" + enc(id) + "/role-mappings/realm", List.of(representation), true);
    }
    public void assignClientRole(String id, String clientId, String role) { clientRole(id, clientId, role, "POST"); }
    public void removeClientRole(String id, String clientId, String role) { clientRole(id, clientId, role, "DELETE"); }
    private void clientRole(String id, String clientId, String role, String method) {
        if (!config.allowedClientRoles().contains(clientId + ":" + role)) throw new IllegalArgumentException("Client role is not allowlisted");
        var clients = tree(call("GET", "/clients?clientId=" + enc(clientId), null, true));
        if (clients.size() != 1) throw new IdentityException(502, "IDENTITY_CONFIGURATION", "Configured role client is missing");
        String clientUuid = clients.get(0).path("id").asText();
        var representation = tree(call("GET", "/clients/" + enc(clientUuid) + "/roles/" + enc(role), null, true));
        call(method, "/users/" + enc(id) + "/role-mappings/clients/" + enc(clientUuid), List.of(representation), true);
    }
    public void sendActions(String id, List<String> actions) {
        if (actions.isEmpty() || !Set.of("VERIFY_EMAIL", "UPDATE_PASSWORD").containsAll(actions))
            throw new IllegalArgumentException("Unsupported identity action");
        // Email delivery is not safely retryable; the caller decides whether to request another email.
        call("PUT", "/users/" + enc(id) + "/execute-actions-email?lifespan=900", actions, false);
    }
    public void deleteOwnedIdentity(String id, UUID provisioningId) {
        var identity = findById(id);
        if (identity.isEmpty()) return;
        if (!List.of(provisioningId.toString()).equals(identity.get().attributes().get(OWNER)))
            throw new IdentityException(409, "IDENTITY_OWNERSHIP", "Identity ownership could not be verified");
        call("DELETE", "/users/" + enc(id), null, true, true);
    }
    private HttpResponse<String> call(String method, String path, Object body, boolean retrySafe) {
        return call(method, path, body, retrySafe, false);
    }
    private HttpResponse<String> call(String method, String path, Object body, boolean retrySafe, boolean allow404) {
        String payload = body == null ? "" : json.writeValueAsString(body);
        for (int attempt=0; ; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/admin/realms/" + enc(config.realm()) + path))
                .timeout(config.readTimeout()).header("Authorization", "Bearer " + token()).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload)).build();
            try {
                var response = send(request);
                int status = response.statusCode();
                if (status >= 200 && status < 300 || status == 404 && allow404) return response;
                if (retrySafe && attempt == 0 && (status == 429 || status >= 500)) { pause(); continue; }
                if (status == 401 || status == 403) {
                    expiresAt = Instant.EPOCH;
                    throw new IdentityException(502, "IDENTITY_ADMIN_DENIED", "Identity service-account authorization failed");
                }
                if (status == 400 && method.equals("POST") && path.equals("/users"))
                    throw new IdentityException(400, "IDENTITY_VALIDATION_FAILED", "Identity details or password do not meet the configured Keycloak policy");
                if (status == 409) throw new IdentityException(409, "IDENTITY_CONFLICT", "Identity already exists; retry with the same idempotency key to reconcile");
                if (status == 429 || status >= 500) throw IdentityException.unavailable();
                throw new IdentityException(502, "IDENTITY_REJECTED", "Identity provider rejected the operation");
            } catch (IOException e) {
                if (retrySafe && attempt == 0) { pause(); continue; }
                throw IdentityException.unavailable();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw IdentityException.unavailable(); }
        }
    }
    private synchronized String token() {
        if (accessToken != null && Instant.now().isBefore(expiresAt)) return accessToken;
        String form = "grant_type=client_credentials&client_id=" + enc(config.clientId()) + "&client_secret=" + enc(config.clientSecret());
        var request = HttpRequest.newBuilder(URI.create(base() + "/realms/" + enc(config.realm()) + "/protocol/openid-connect/token"))
            .timeout(config.readTimeout()).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        try {
            var response = send(request);
            if (response.statusCode() == 401 || response.statusCode() == 403)
                throw new IdentityException(502, "IDENTITY_ADMIN_DENIED", "Identity service-account authentication failed");
            if (response.statusCode() != 200) throw IdentityException.unavailable();
            JsonNode data = tree(response);
            String token = data.path("access_token").asText();
            if (token.isBlank()) throw IdentityException.unavailable();
            accessToken = token; expiresAt = Instant.now().plusSeconds(Math.max(0, data.path("expires_in").asLong() - 30));
            return token;
        } catch (IOException e) { throw IdentityException.unavailable(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw IdentityException.unavailable(); }
    }
    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        try {
            var response=http.send(request, HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()>=400) metrics.counter("user.identity.keycloak.failures", "reason", "http").increment();
            return response;
        } catch(IOException failure) {
            metrics.counter("user.identity.keycloak.failures", "reason", "io").increment(); throw failure;
        } catch(InterruptedException failure) {
            metrics.counter("user.identity.keycloak.failures", "reason", "interrupted").increment(); throw failure;
        }
    }
    private JsonNode tree(HttpResponse<String> response) {
        try { return json.readTree(response.body()); }
        catch (RuntimeException e) { throw new IdentityException(502, "IDENTITY_RESPONSE", "Invalid identity provider response"); }
    }
    private String base() { return config.adminBaseUrl().replaceAll("/+$", ""); }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static void pause() {
        try { Thread.sleep(150); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw IdentityException.unavailable(); }
    }
}
