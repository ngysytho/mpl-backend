package com.example.userservice.modules.user.service;

import com.example.userservice.core.exception.ServiceException;
import com.example.userservice.modules.user.dto.request.RegisterUserCommand;
import com.example.userservice.modules.user.repository.RegistrationRateLimiter;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class UserRegistrationService {
    private final ProvisioningPort provisioning;
    private final IdentitySyncPort sync;
    private final RegistrationRateLimiter limiter;

    public UserRegistrationService(ProvisioningPort provisioning, IdentitySyncPort sync, RegistrationRateLimiter limiter) {
        this.provisioning = provisioning; this.sync = sync; this.limiter = limiter;
    }

    public void register(UUID key, RegisterUserCommand command, String remoteAddress) {
        limiter.check(remoteAddress);
        provisioning.reserveRegistration(key, command);
        var user = provisioning.provisionRegistration(key, command);
        if (!sync.synchronize(user.id())) {
            throw new ServiceException(503, "IDENTITY_SYNC_PENDING",
                "Registration is pending. Retry with the original Idempotency-Key and profile fields.");
        }
        // Do not send repeat emails, issue JWTs or reset a password on an anonymous replay.
    }
}
