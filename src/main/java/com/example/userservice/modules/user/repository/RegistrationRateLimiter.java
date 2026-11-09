package com.example.userservice.modules.user.repository;

import com.example.userservice.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

/** Shared fixed-hour quota across API instances. Only trust the socket peer, not client-supplied forwarding headers. */
@Component
public class RegistrationRateLimiter {
    private final JdbcTemplate jdbc;
    private final int maxAttempts;
    public RegistrationRateLimiter(JdbcTemplate jdbc, @Value("${app.registration.max-attempts-per-hour:10}") int maxAttempts) {
        if (maxAttempts < 1) throw new IllegalArgumentException("Registration quota must be positive");
        this.jdbc = jdbc; this.maxAttempts = maxAttempts;
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void check(String remoteAddress) {
        jdbc.update("DELETE FROM user_registration_rate_limits WHERE window_start < now() - interval '2 days'");
        var attempts = jdbc.queryForList("""
            INSERT INTO user_registration_rate_limits (client_hash, window_start, attempts)
            VALUES (?, date_trunc('hour', CURRENT_TIMESTAMP), 1)
            ON CONFLICT (client_hash, window_start) DO UPDATE
            SET attempts = user_registration_rate_limits.attempts + 1
            WHERE user_registration_rate_limits.attempts < ?
            RETURNING attempts
            """, Integer.class, hash(remoteAddress), maxAttempts);
        if (attempts.isEmpty()) throw new ServiceException(429, "REGISTRATION_RATE_LIMITED", "Too many registration attempts. Try again later.");
    }
    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
