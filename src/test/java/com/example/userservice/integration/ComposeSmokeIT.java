package com.example.userservice.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Invoked only by acceptance.sh against a newly created Compose project. Never prints tokens or payloads. */
@EnabledIfEnvironmentVariable(named = "MPL_COMPOSE_TESTS", matches = "true")
class ComposeSmokeIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    @Test void registrationPkceAuthorizationAndMetrics() throws Exception {
        assertEquals("true", System.getenv("MPL_DISPOSABLE_KEYCLOAK"), "Disposable server opt-in required");
        String api = loopback(System.getenv("API_URL"));
        String kc = loopback(System.getenv("KEYCLOAK_ADMIN_URL"));
        String owner = UUID.randomUUID().toString();
        String email = "acceptance-" + owner + "@example.invalid";
        String password = UUID.randomUUID() + "aA9!";
        String body = JSON.writeValueAsString(Map.of("fullName", "Disposable Acceptance", "email", email, "password", password));
        for (int i = 0; i < 2; i++) {
            var response = send(HttpRequest.newBuilder(URI.create(api + "/api/v1/users/register"))
                .header("Content-Type", "application/json").header("Idempotency-Key", owner)
                .POST(HttpRequest.BodyPublishers.ofString(body)));
            assertEquals(201, response.statusCode(), "Registration/replay status");
        }
        String token = pkce(kc, api + "/swagger-ui/oauth2-redirect.html", email, password);
        JsonNode claims = claims(token);
        assertEquals(kc + "/realms/user-service", claims.path("iss").asText());
        assertTrue(contains(claims.path("aud"), "user-service-api"), "API audience");
        assertTrue(contains(claims.path("realm_access").path("roles"), "USER"));
        assertFalse(contains(claims.path("realm_access").path("roles"), "ADMIN"));
        assertEquals("UP", get(api + "/actuator/health/readiness", null, 200).path("status").asText());
        get(api + "/api/v1/users/me", null, 401);
        assertFalse(get(api + "/api/v1/users/me", token, 200).path("data").path("id").asText().isBlank());
        var smoke = new ProcessBuilder("bash", "scripts/smoke.sh")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        smoke.environment().put("API_URL", api);
        smoke.environment().put("SMOKE_ACCESS_TOKEN", token);
        var smokeProcess = smoke.start();
        if (!smokeProcess.waitFor(65, java.util.concurrent.TimeUnit.SECONDS)) {
            smokeProcess.destroyForcibly();
            fail("Bash smoke timed out");
        }
        assertEquals(0, smokeProcess.exitValue(), "Bash deployment smoke");
        get(api + "/api/v1/users", token, 403);
        get(api + "/actuator/metrics", token, 403);

        // Only the fixture identity with this test's ownership marker may be granted ADMIN.
        String bootstrap = form(kc + "/realms/master/protocol/openid-connect/token", Map.of(
            "grant_type", "password", "client_id", "admin-cli", "username", required("KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME"),
            "password", required("KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD"))).path("access_token").asText();
        String admin = kc + "/admin/realms/user-service";
        String subject = claims.path("sub").asText();
        assertDoesNotThrow(() -> UUID.fromString(subject), "Expected opaque UUID subject");
        JsonNode identity = get(admin + "/users/" + subject, bootstrap, 200);
        assertEquals(email, identity.path("email").asText());
        JsonNode ownership = identity.path("attributes").path("mplProvisioningId");
        assertEquals(1, ownership.size());
        assertEquals(owner, ownership.get(0).asText());
        JsonNode role = get(admin + "/roles/ADMIN", bootstrap, 200);
        var assignment = send(HttpRequest.newBuilder(URI.create(admin + "/users/" + subject + "/role-mappings/realm"))
            .header("Authorization", "Bearer " + bootstrap).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(List.of(role)))));
        assertEquals(204, assignment.statusCode(), "Fixture role assignment");
        String adminToken = pkce(kc, api + "/swagger-ui/oauth2-redirect.html", email, password);
        assertTrue(contains(claims(adminToken).path("realm_access").path("roles"), "ADMIN"));
        assertEquals(1, get(api + "/api/v1/users?size=1", adminToken, 200).path("data").path("totalElements").asInt());
        Map<String, JsonNode> metrics = new LinkedHashMap<>();
        for (String name : List.of("user.identity.sync.pending", "user.identity.provisioning.incomplete",
            "user.identity.sync.attempts", "user.identity.sync.delivery.duration", "user.identity.sync.lock.wait", "user.identity.sync.latency")) {
            metrics.put(name, get(api + "/actuator/metrics/" + name, adminToken, 200).path("measurements"));
        }
        for (String name : List.of("user.identity.sync.pending", "user.identity.provisioning.incomplete")) {
            assertEquals(0.0, metrics.get(name).get(0).path("value").asDouble(), "Journal must be reconciled");
        }
        Files.writeString(Path.of(required("MPL_ACCEPTANCE_METRICS")), JSON.writeValueAsString(metrics));
    }

    private String pkce(String base, String redirect, String email, String password) throws Exception {
        String verifier = UUID.randomUUID().toString() + UUID.randomUUID();
        String state = UUID.randomUUID().toString();
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String auth = base + "/realms/user-service/protocol/openid-connect/auth?" + encoded(Map.of(
            "client_id", "user-service-frontend", "response_type", "code", "scope", "openid", "redirect_uri", redirect,
            "code_challenge", challenge, "code_challenge_method", "S256", "state", state));
        var page = send(HttpRequest.newBuilder(URI.create(auth)).GET());
        assertEquals(200, page.statusCode(), "Login page status");
        String action = null;
        var forms = Pattern.compile("<form\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(page.body());
        while (forms.find()) {
            Map<String, String> attrs = attributes(forms.group());
            if ("kc-form-login".equals(attrs.get("id"))) action = attrs.get("action");
        }
        assertNotNull(action, "Keycloak login form missing");
        URI actionUri = URI.create(action);
        assertTrue(sameOrigin(URI.create(base), actionUri), "Login form must stay on the fixture origin");
        Map<String, String> fields = new LinkedHashMap<>();
        var inputs = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE).matcher(page.body());
        while (inputs.find()) {
            Map<String, String> attrs = attributes(inputs.group());
            if ("hidden".equals(attrs.get("type")) && attrs.containsKey("name")) fields.put(attrs.get("name"), attrs.getOrDefault("value", ""));
        }
        fields.put("username", email); fields.put("password", password);
        // Browsers send Keycloak Secure cookies on trustworthy localhost HTTP. Restrict this exception
        // to this one loopback origin and its login path; never forward cookies across redirects.
        String cookies = loginCookies(page.headers().allValues("Set-Cookie"), actionUri);
        var login = send(HttpRequest.newBuilder(actionUri).header("Cookie", cookies)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encoded(fields))));
        assertTrue(login.statusCode() == 302 || login.statusCode() == 303, "PKCE login must finish with a redirect");
        URI location = URI.create(login.headers().firstValue("Location").orElseThrow());
        URI expected = URI.create(redirect);
        assertTrue(sameOrigin(expected, location) && expected.getPath().equals(location.getPath()), "Unexpected OAuth redirect target");
        Map<String, String> params = new HashMap<>();
        for (String pair : location.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        assertEquals(state, params.get("state"), "OAuth state mismatch");
        assertNotNull(params.get("code"), "Authorization code missing");
        String token = form(base + "/realms/user-service/protocol/openid-connect/token", Map.of(
            "grant_type", "authorization_code", "client_id", "user-service-frontend", "code", params.get("code"),
            "redirect_uri", redirect, "code_verifier", verifier)).path("access_token").asText();
        assertFalse(token.isBlank(), "Access token missing");
        return token;
    }

    static String loginCookies(List<String> setCookies, URI action) {
        if (!Set.of("localhost", "127.0.0.1", "[::1]").contains(action.getHost())) throw new IllegalArgumentException("Loopback only");
        List<String> result = new ArrayList<>();
        for (String raw : setCookies) {
            String[] parts = raw.split(";"); boolean allowed = true;
            for (int i = 1; i < parts.length; i++) {
                String attr = parts[i].trim();
                if (attr.regionMatches(true, 0, "Path=", 0, 5)) {
                    String path = attr.substring(5);
                    allowed &= action.getPath().equals(path) || action.getPath().startsWith(path.endsWith("/") ? path : path + "/");
                }
                if (attr.regionMatches(true, 0, "Domain=", 0, 7)) allowed &= attr.substring(7).equalsIgnoreCase(action.getHost());
                if (attr.equalsIgnoreCase("Max-Age=0")) allowed = false;
            }
            if (allowed) result.add(parts[0].trim());
        }
        return String.join("; ", result);
    }
    static boolean sameOrigin(URI a, URI b) {
        return Objects.equals(a.getScheme(), b.getScheme()) && Objects.equals(a.getHost(), b.getHost()) && a.getPort() == b.getPort();
    }
    private static Map<String, String> attributes(String tag) {
        var matcher = Pattern.compile("([\\w-]+)\\s*=\\s*([\"'])(.*?)\\2", Pattern.DOTALL).matcher(tag);
        Map<String, String> result = new HashMap<>();
        while (matcher.find()) result.put(matcher.group(1).toLowerCase(Locale.ROOT), HtmlUtils.htmlUnescape(matcher.group(3)));
        return result;
    }
    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode get(String url, String token, int status) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        var response = send(request);
        assertEquals(status, response.statusCode(), "HTTP status for protected/health endpoint");
        return status == 200 ? JSON.readTree(response.body()) : JSON.nullNode();
    }
    private JsonNode form(String url, Map<String, String> fields) throws Exception {
        var response = send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encoded(fields))));
        assertEquals(200, response.statusCode(), "Token endpoint status");
        return JSON.readTree(response.body());
    }
    private static String encoded(Map<String, String> fields) {
        return fields.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
            URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).reduce((a, b) -> a + "&" + b).orElse("");
    }
    private static JsonNode claims(String token) {
        return JSON.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    }
    private static boolean contains(JsonNode values, String expected) {
        if (values.isString()) return expected.equals(values.asText());
        for (JsonNode value : values) if (expected.equals(value.asText())) return true;
        return false;
    }
    private static String required(String name) { return Objects.requireNonNull(System.getenv(name), "Missing " + name); }
    private static String loopback(String value) {
        URI uri = URI.create(value);
        assertTrue(Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost()) && "http".equals(uri.getScheme()), "Fixture must be loopback HTTP");
        return value;
    }
}
