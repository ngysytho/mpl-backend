package com.example.userservice.integration.keycloak;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import java.net.http.HttpClient;
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakConfig {
    @Bean("keycloakHttpClient") HttpClient keycloakHttpClient(KeycloakProperties p) {
        return HttpClient.newBuilder().connectTimeout(p.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }
}
