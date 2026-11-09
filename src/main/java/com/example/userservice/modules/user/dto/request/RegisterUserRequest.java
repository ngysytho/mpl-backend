package com.example.userservice.modules.user.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record RegisterUserRequest(
    @NotBlank @Size(max=200) String fullName,
    @NotBlank @Email @Size(max=254) String email,
    @NotBlank @Size(min=14, max=128)
    @JsonProperty(access=JsonProperty.Access.WRITE_ONLY)
    @Schema(accessMode=Schema.AccessMode.WRITE_ONLY, format="password",
        description="14–128 characters; Keycloak enforces its configured password policy") String password,
    @Size(max=30) String phoneNumber,
    @Size(max=2048) String avatarUrl) {
    public RegisterUserCommand command() {
        return new RegisterUserCommand(new CreateUserCommand(fullName, email, phoneNumber, avatarUrl), password);
    }
    @Override public String toString() { return "RegisterUserRequest[REDACTED]"; }
}
