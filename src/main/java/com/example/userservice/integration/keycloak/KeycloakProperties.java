package com.example.userservice.integration.keycloak;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;
import java.util.Set;
@Validated
@ConfigurationProperties("app.keycloak")
public record KeycloakProperties(@NotBlank String adminBaseUrl, @NotBlank @Pattern(regexp="[a-zA-Z0-9_-]+") String realm,
    @NotBlank String clientId, @NotBlank String clientSecret, @NotNull Duration connectTimeout, @NotNull Duration readTimeout,
    @NotNull Set<String> allowedRealmRoles, @NotNull Set<String> allowedClientRoles) {
    @Override public String toString() { return "KeycloakProperties[realm=" + realm + ", clientId=" + clientId + ", secret=REDACTED]"; }
}
