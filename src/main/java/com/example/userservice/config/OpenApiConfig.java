package com.example.userservice.config;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {
    @Bean OpenAPI api(@Value("${app.security.issuer}") String issuer) {
        return new OpenAPI().info(new Info().title("mpl-be User API").version("v1")
            .description("Keycloak bearer access token required. Creation requires a stable UUID Idempotency-Key. Email is immutable."))
            .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
                .addSecuritySchemes("keycloak", new SecurityScheme().type(SecurityScheme.Type.OAUTH2)
                    .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                        .authorizationUrl(issuer + "/protocol/openid-connect/auth")
                        .tokenUrl(issuer + "/protocol/openid-connect/token")
                        .refreshUrl(issuer + "/protocol/openid-connect/token")
                        .scopes(new Scopes().addString("openid", "Sign in"))))))
            .addSecurityItem(new SecurityRequirement().addList("keycloak"));
    }
}
