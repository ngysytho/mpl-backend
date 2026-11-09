package com.example.userservice.security;
import com.example.userservice.core.security.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class JwtValidationTest {
    @Test void verifiesSignatureIssuerExpiryAndAudience() throws Exception {
        var key = new RSAKeyGenerator(2048).generate();
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("https://issuer/realms/user-service"), new AudienceValidator("user-service-api")));
        assertThat(decoder.decode(token(key, "https://issuer/realms/user-service", "user-service-api", Instant.now().plusSeconds(300))).getSubject()).isEqualTo("subject");
        assertThatThrownBy(() -> decoder.decode(token(key, "https://other", "user-service-api", Instant.now().plusSeconds(300)))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(key, "https://issuer/realms/user-service", "other-api", Instant.now().plusSeconds(300)))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token(key, "https://issuer/realms/user-service", "user-service-api", Instant.now().minusSeconds(120)))).isInstanceOf(JwtException.class);
        var other = new RSAKeyGenerator(2048).generate();
        assertThatThrownBy(() -> decoder.decode(token(other, "https://issuer/realms/user-service", "user-service-api", Instant.now().plusSeconds(300)))).isInstanceOf(JwtException.class);
    }
    @Test void missingAudienceSubjectOrExpiryFailsClosed() {
        var validator = new AudienceValidator("user-service-api");
        var missingAudience = Jwt.withTokenValue("t").header("alg","RS256").subject("subject").expiresAt(Instant.now().plusSeconds(300)).build();
        var missingSubject = Jwt.withTokenValue("t").header("alg","RS256").claim("aud", List.of("user-service-api")).expiresAt(Instant.now().plusSeconds(300)).build();
        var missingExpiry = Jwt.withTokenValue("t").header("alg","RS256").subject("subject").claim("aud", List.of("user-service-api")).build();
        assertThat(validator.validate(missingAudience).hasErrors()).isTrue();
        assertThat(validator.validate(missingSubject).hasErrors()).isTrue();
        assertThat(validator.validate(missingExpiry).hasErrors()).isTrue();
    }
    @Test void mapsOnlyConfiguredRealmAndResourceRoles() {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject("subject")
            .claim("realm_access", Map.of("roles", List.of("USER", "realm-admin")))
            .claim("resource_access", Map.of("user-service-api", Map.of("roles", List.of("ADMIN")), "other", Map.of("roles", List.of("ADMIN")))).build();
        assertThat(new KeycloakRolesConverter("user-service-api").convert(jwt)).extracting("authority").containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }
    private String token(com.nimbusds.jose.jwk.RSAKey key, String issuer, String audience, Instant expires) throws Exception {
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder().subject("subject").issuer(issuer)
            .audience(audience).issueTime(Date.from(Instant.now().minusSeconds(300))).expirationTime(Date.from(expires)).build());
        jwt.sign(new RSASSASigner(key)); return jwt.serialize();
    }
}
