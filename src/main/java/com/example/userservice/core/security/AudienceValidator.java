package com.example.userservice.core.security;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
public record AudienceValidator(String audience) implements OAuth2TokenValidator<Jwt> {
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        return jwt.getAudience() != null && jwt.getAudience().contains(audience) && jwt.getSubject() != null && !jwt.getSubject().isBlank() && jwt.getExpiresAt() != null
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Missing required audience, subject or expiry", null));
    }
}
