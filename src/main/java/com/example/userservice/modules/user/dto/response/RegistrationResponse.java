package com.example.userservice.modules.user.dto.response;

/** Anonymous retries return no profile, identity IDs or tokens. */
public record RegistrationResponse(String message, String nextAction) {
    public static RegistrationResponse accepted() {
        return new RegistrationResponse("Registration successful. Sign in through Keycloak.", "SIGN_IN");
    }
}
