package com.example.userservice.modules.user.dto.response;
import com.example.userservice.modules.user.entity.*;
import java.time.Instant;
import java.util.UUID;
public record UserResponse(UUID id, String keycloakUserId, String fullName, String email, String phoneNumber,
                           String avatarUrl, UserStatus status, Instant createdAt, Instant updatedAt) {
    public static UserResponse from(User u) { return new UserResponse(u.id(), u.keycloakUserId(), u.fullName(), u.email(),
        u.phoneNumber(), u.avatarUrl(), u.status(), u.createdAt(), u.updatedAt()); }
}
