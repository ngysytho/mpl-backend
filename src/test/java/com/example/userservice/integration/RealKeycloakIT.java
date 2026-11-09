package com.example.userservice.integration;
import com.example.userservice.integration.keycloak.KeycloakAdminClient;
import com.example.userservice.integration.keycloak.KeycloakProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
/** Opt-in: disposable realm configured by keycloak-config-cli, never a production realm. */
@EnabledIfEnvironmentVariable(named="MPL_REAL_KEYCLOAK_TESTS", matches="true")
class RealKeycloakIT {
    @Test void serviceAccountCanManageAnOwnedTestIdentity() {
        assertThat(System.getenv("MPL_DISPOSABLE_KEYCLOAK"))
            .as("Destructive lifecycle tests require an explicitly disposable server").isEqualTo("true");
        var properties=new KeycloakProperties(System.getenv("KEYCLOAK_ADMIN_URL"),"user-service",
            System.getenv("KEYCLOAK_CLIENT_ID"),System.getenv("KEYCLOAK_CLIENT_SECRET"),Duration.ofSeconds(3),Duration.ofSeconds(5),Set.of("USER"),Set.of("user-service-api:USER"));
        var client=new KeycloakAdminClient(properties,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        var owner=UUID.randomUUID(); String email="smoke-"+owner+"@example.invalid";
        String id=client.createDisabled(email,"Integration Test",owner);
        try {
            assertThat(client.findByExactUsername(email).orElseThrow().id()).isEqualTo(id);
            assertThat(client.findById(id).orElseThrow().enabled()).isFalse();
            client.assignRealmRole(id,"USER"); client.removeRealmRole(id,"USER"); client.assignRealmRole(id,"USER");
            client.assignClientRole(id,"user-service-api","USER"); client.removeClientRole(id,"user-service-api","USER");
            assertThat(client.findById(id).orElseThrow().attributes().get("mplProvisioningId")).containsExactly(owner.toString());
            client.updateProfile(id,"Updated Integration Test");client.updateProfile(id,"Updated Integration Test");
            assertThat(client.findById(id).orElseThrow().fullName()).isEqualTo("Updated Integration Test");
            client.setEnabled(id,true);
            assertThat(client.findById(id).orElseThrow().enabled()).isTrue();
            client.setEnabled(id,false); assertThat(client.findById(id).orElseThrow().enabled()).isFalse();
            client.setEnabled(id,true);assertThat(client.findById(id).orElseThrow().enabled()).isTrue();
        } finally { client.deleteOwnedIdentity(id,owner); }
        assertThat(client.findById(id)).isEmpty();
    }
}
