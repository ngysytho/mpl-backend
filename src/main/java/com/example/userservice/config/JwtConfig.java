package com.example.userservice.config;
import com.example.userservice.core.security.AudienceValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.client.RestTemplate;
@Configuration(proxyBeanMethods = false)
public class JwtConfig {
    @Bean JwtDecoder jwtDecoder(@Value("${app.security.issuer}") String issuer, @Value("${app.security.jwk-set-uri}") String jwks,
                                @Value("${app.security.audience}") String audience) {
        var factory = new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).restOperations(new RestTemplate(factory)).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), new AudienceValidator(audience)));
        return decoder;
    }
}
