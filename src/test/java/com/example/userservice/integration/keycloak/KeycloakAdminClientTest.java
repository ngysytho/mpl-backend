package com.example.userservice.integration.keycloak;
import com.example.userservice.integration.keycloak.KeycloakAdminClient;
import com.example.userservice.integration.keycloak.KeycloakProperties;
import com.example.userservice.integration.keycloak.IdentityException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
class KeycloakAdminClientTest {
    private HttpServer server;
    private ExecutorService executor;
    private KeycloakAdminClient client;
    private final Queue<Reply> replies = new ConcurrentLinkedQueue<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    record Reply(int status, String body, long delayMillis) {
        Reply(int status,String body) {this(status,body,0);}
    }
    private io.micrometer.core.instrument.simple.SimpleMeterRegistry metrics;
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); executor=Executors.newCachedThreadPool(); server.setExecutor(executor);
        server.createContext("/", exchange -> {
            String path=exchange.getRequestURI().toString();
            if (path.endsWith("/token")) {
                byte[] token="{\"access_token\":\"service-token\",\"expires_in\":300}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,token.length); exchange.getResponseBody().write(token); exchange.close(); return;
            }
            requests.add(exchange.getRequestMethod()+" "+path+" "+new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            if (!"Bearer service-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401,-1); exchange.close(); return;
            }
            Reply reply=replies.poll(); if (reply==null) reply=new Reply(500, "{}");
            if (reply.delayMillis()>0) {
                try {Thread.sleep(reply.delayMillis());} catch(InterruptedException e){Thread.currentThread().interrupt();}
            }
            if (reply.status()==201) exchange.getResponseHeaders().add("Location","http://127.0.0.1/admin/realms/user-service/users/new-id");
            byte[] body=reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(),reply.status()==204 ? -1 : body.length);
            if (reply.status()!=204) exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        var props=new KeycloakProperties("http://127.0.0.1:"+server.getAddress().getPort(),"user-service","service","test-only",
            Duration.ofSeconds(1),Duration.ofSeconds(1),Set.of("USER"),Set.of("user-service-api:USER"));
        metrics=new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        client=new KeycloakAdminClient(props,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),metrics);
    }
    @AfterEach void close() { server.stop(0); executor.shutdownNow(); }
    @Test void provisionsDisabledIdentityWithOwnershipMarker() {
        replies.add(new Reply(201,"")); var key=UUID.randomUUID();
        assertThat(client.createDisabled("a@example.com","Name",key)).isEqualTo("new-id");
        assertThat(requests).singleElement().asString().contains("\"enabled\":false", key.toString(), "UPDATE_PASSWORD");
    }
    @Test void neverRetriesCreateOnTransientFailure() {
        replies.add(new Reply(503,"{}"));
        assertThatThrownBy(() -> client.createDisabled("a@example.com","Name",UUID.randomUUID())).isInstanceOf(IdentityException.class);
        assertThat(requests).hasSize(1);
    }
    @Test void mapsDuplicateIdentity() {
        replies.add(new Reply(409,"{}"));
        assertThatThrownBy(() -> client.createDisabled("a@example.com","Name",UUID.randomUUID())).isInstanceOfSatisfying(IdentityException.class,e -> assertThat(e.status()).isEqualTo(409));
    }
    @Test void mapsAdminAuthorizationFailureWithoutExposingBody() {
        replies.add(new Reply(403,"sensitive upstream content"));
        assertThatThrownBy(() -> client.findById("id")).isInstanceOfSatisfying(IdentityException.class,e -> {
            assertThat(e.code()).isEqualTo("IDENTITY_ADMIN_DENIED"); assertThat(e.getMessage()).doesNotContain("sensitive");
        });
    }
    @Test void safelyRetriesLookupOnce() {
        replies.add(new Reply(503,"{}")); replies.add(new Reply(200,"[]"));
        assertThat(client.findByExactEmail("a+b@example.com")).isEmpty();
        assertThat(requests).hasSize(2); assertThat(requests.getFirst()).contains("exact=true", "a%2Bb%40example.com");
    }
    @Test void unavailableAfterBoundedRetry() {
        replies.add(new Reply(503,"{}")); replies.add(new Reply(503,"{}"));
        assertThatThrownBy(() -> client.findById("id")).isInstanceOfSatisfying(IdentityException.class,e -> assertThat(e.status()).isEqualTo(503));
        assertThat(requests).hasSize(2);
    }
    @Test void disablesAndLogsOutSessions() {
        replies.add(new Reply(204,"")); replies.add(new Reply(204,"")); client.setEnabled("id",false);
        assertThat(requests.get(0)).contains("PUT", "\"enabled\":false"); assertThat(requests.get(1)).contains("POST", "/logout");
    }
    @Test void assignsOnlyAllowlistedRole() {
        replies.add(new Reply(200,"{\"id\":\"role-id\",\"name\":\"USER\"}")); replies.add(new Reply(204,""));
        client.assignRealmRole("id","USER"); assertThat(requests.get(1)).contains("/role-mappings/realm","role-id");
        assertThatThrownBy(() -> client.assignRealmRole("id","ADMIN")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void refusesCompensationOfUnrelatedIdentity() {
        replies.add(new Reply(200,"{\"id\":\"id\",\"attributes\":{}}"));
        assertThatThrownBy(() -> client.deleteOwnedIdentity("id",UUID.randomUUID())).isInstanceOf(IdentityException.class);
        assertThat(requests).hasSize(1);
    }

    @Test void selfRegistrationCreatesCredentialOnceWithoutTemporaryPasswordOrAdminRole() {
        replies.add(new Reply(201,""));
        client.createDisabledWithPassword("new@example.com","Name",UUID.randomUUID(),"Example-Password-42!");
        assertThat(requests).singleElement().asString().contains("\"credentials\"", "\"temporary\":false", "\"enabled\":false",
            "\"emailVerified\":false").doesNotContain("VERIFY_EMAIL", "UPDATE_PASSWORD", "ADMIN");
    }
    @Test void registrationPasswordPolicyFailureIsSanitizedAndNotRetried() {
        replies.add(new Reply(400,"{\"errorMessage\":\"sensitive upstream details\"}"));
        assertThatThrownBy(() -> client.createDisabledWithPassword("new@example.com","Name",UUID.randomUUID(),"Example-Password-42!"))
            .isInstanceOfSatisfying(IdentityException.class,e -> {
                assertThat(e.status()).isEqualTo(400); assertThat(e.code()).isEqualTo("IDENTITY_VALIDATION_FAILED");
                assertThat(e.getMessage()).doesNotContain("sensitive", "Example-Password");
            });
        assertThat(requests).hasSize(1);
    }
    @Test void registrationCreateTimeoutEquivalentDoesNotCauseBlindRetry() {
        replies.add(new Reply(503,"{}"));
        assertThatThrownBy(() -> client.createDisabledWithPassword("new@example.com","Name",UUID.randomUUID(),"Example-Password-42!"))
            .isInstanceOf(IdentityException.class);
        assertThat(requests).hasSize(1);
    }
    @Test void actualNetworkTimeoutAfterCreateIsNotRetriedAndIsCounted() {
        replies.add(new Reply(201,"",1500));
        assertThatThrownBy(()->client.createDisabled("timeout@example.com","Name",UUID.randomUUID())).isInstanceOf(IdentityException.class);
        assertThat(requests).hasSize(1);
        assertThat(metrics.get("user.identity.keycloak.failures").tag("reason","io").counter().count()).isEqualTo(1);
    }
}
