package com.example.userservice.modules.user.dto.request;

import java.util.Objects;

/** Password exists only for the Keycloak create call; never persist it or put it in a request hash. */
public record RegisterUserCommand(CreateUserCommand profile, String password) {
    public RegisterUserCommand {
        Objects.requireNonNull(profile);
        if (password == null || password.isBlank() || password.length() < 14 || password.length() > 128)
            throw new IllegalArgumentException("Password must contain 14–128 characters");
    }
    @Override public String toString() { return "RegisterUserCommand[REDACTED]"; }
}
