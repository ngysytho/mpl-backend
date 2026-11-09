package com.example.userservice.modules.user.dto.request;
import com.example.userservice.modules.user.entity.*;
import java.time.Instant;
import java.util.UUID;
public record CreateUserCommand(String fullName, String email, String phoneNumber, String avatarUrl) {
    public CreateUserCommand {
        // Validate before reserving a durable provisioning request or calling an external system.
        User validated = new User(UUID.randomUUID(), "validation", fullName, email, phoneNumber, avatarUrl,
                                  UserStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH);
        fullName = validated.fullName(); email = validated.email();
    }
}
