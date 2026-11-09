package com.example.userservice.modules.user.entity;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
public record User(UUID id, String keycloakUserId, String fullName, String email, String phoneNumber, String avatarUrl, UserStatus status, Instant createdAt, Instant updatedAt) {
    public User {
        Objects.requireNonNull(id); Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        if (keycloakUserId == null || keycloakUserId.isBlank() || keycloakUserId.length() > 255)
            throw new IllegalArgumentException("Invalid identity identifier");
        if (fullName == null || fullName.isBlank() || fullName.strip().length() > 200)
            throw new IllegalArgumentException("Full name must contain 1–200 characters");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("Update timestamp precedes creation");
        fullName = fullName.strip(); email = normalizeEmail(email);
        if (phoneNumber != null && !phoneNumber.matches("\\+?[0-9 ()-]{7,30}"))
            throw new IllegalArgumentException("Invalid phone number");
        if (avatarUrl != null) {
            URI uri = URI.create(avatarUrl);
            if (avatarUrl.length() > 2048 || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException("Avatar must be an absolute HTTPS URL without credentials");
        }
    }
    public static String normalizeEmail(String email) {
        if (email == null) throw new IllegalArgumentException("Email is required");
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Invalid email");
        return normalized;
    }
    public User withProfile(String name, String phone, String avatar, Instant now) {
        return new User(id, keycloakUserId, name, email, phone, avatar, status, createdAt, now);
    }
    public User withStatus(UserStatus next, Instant now) {
        return new User(id, keycloakUserId, fullName, email, phoneNumber, avatarUrl, next, createdAt, now);
    }
}
