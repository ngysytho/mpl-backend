package com.example.userservice.integration.keycloak;
import com.example.userservice.core.exception.ServiceException;
public final class IdentityException extends ServiceException {
    public IdentityException(int status, String code, String message) { super(status, code, message); }
    public static IdentityException unavailable() { return new IdentityException(503, "IDENTITY_UNAVAILABLE", "Identity provider is unavailable; retry with the same idempotency key"); }
}
