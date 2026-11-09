package com.example.userservice.modules.user.repository;
import com.example.userservice.core.exception.ServiceException;
import com.example.userservice.modules.user.dto.request.CreateUserCommand;
import com.example.userservice.modules.user.dto.request.RegisterUserCommand;
import com.example.userservice.modules.user.service.*;
import com.example.userservice.modules.user.entity.*;
import com.example.userservice.modules.user.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;
@Component
public class ProvisioningAdapter implements ProvisioningPort {
    public static final String OWNER_ATTRIBUTE = "mplProvisioningId";
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final IdentityManagementPort identities;
    private final IdentitySyncPort sync;
    private final Clock clock;
    public ProvisioningAdapter(JdbcTemplate jdbc, UserRepository users, IdentityManagementPort identities, IdentitySyncPort sync, Clock clock) {
        this.jdbc=jdbc; this.users=users; this.identities=identities; this.sync=sync; this.clock=clock;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(UUID key, CreateUserCommand command) { reserveRow(key, command, "ADMIN"); }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveRegistration(UUID key, RegisterUserCommand command) {
        reserveRow(key, command.profile(), "SELF_REGISTRATION");
    }

    private void reserveRow(UUID key, CreateUserCommand command, String kind) {
        jdbc.update("INSERT INTO user_provisioning_requests (id, request_hash, email, request_kind) VALUES (?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                    key, fingerprint(command), command.email(), kind);
        var stored = jdbc.queryForMap("SELECT request_hash, request_kind FROM user_provisioning_requests WHERE id=?", key);
        if (!fingerprint(command).equals(stored.get("request_hash")) || !kind.equals(stored.get("request_kind")))
            throw conflict("Idempotency key was used with a different request or operation");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 60)
    public User provision(UUID key, CreateUserCommand command) { return complete(key, command, "ADMIN", null); }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 60)
    public User provisionRegistration(UUID key, RegisterUserCommand command) {
        return complete(key, command.profile(), "SELF_REGISTRATION", command.password());
    }

    private User complete(UUID key, CreateUserCommand command, String kind, String password) {
        // Password is never journaled or fingerprinted. The first successful Keycloak creation wins;
        // replay/recovery never resets a credential, even if a different password is supplied.
        var rows = jdbc.queryForList("SELECT request_hash, request_kind, user_id FROM user_provisioning_requests WHERE id=? FOR UPDATE", key);
        if (rows.size() != 1 || !fingerprint(command).equals(rows.getFirst().get("request_hash"))
                || !kind.equals(rows.getFirst().get("request_kind"))) throw conflict("Invalid provisioning reservation");
        Object completedId = rows.getFirst().get("user_id");
        if (completedId != null) return users.findById((UUID) completedId).orElseThrow();
        if (users.existsByEmail(command.email())) throw conflict("Email is already registered");
        var existing = identities.findByExactUsername(command.email());
        String identityId;
        if (existing.isPresent()) {
            var identity = existing.get();
            if (!List.of(key.toString()).equals(identity.attributes().get(OWNER_ATTRIBUTE)) || !command.email().equals(identity.email()))
                throw conflict("An unrelated identity already uses this email; manual reconciliation is required");
            identityId = identity.id();
        } else {
            if (identities.findByExactEmail(command.email()).isPresent()) throw conflict("An identity already uses this email");
            // Never blindly retry this POST. A timeout is recovered on the next attempt by exact username + ownership marker.
            identityId = "SELF_REGISTRATION".equals(kind)
                ? identities.createDisabledWithPassword(command.email(), command.fullName(), key, password)
                : identities.createDisabled(command.email(), command.fullName(), key);
        }
        var now = clock.instant();
        User user = users.save(new User(UUID.randomUUID(), identityId, command.fullName(), command.email(), command.phoneNumber(),
                                       command.avatarUrl(), UserStatus.ACTIVE, now, now));
        sync.enqueue(user.id());
        jdbc.update("UPDATE user_provisioning_requests SET user_id=?, completed_at=now() WHERE id=?", user.id(), key);
        return user;
    }
    private static ServiceException conflict(String message) { return new ServiceException(409, "PROVISIONING_CONFLICT", message); }
    static String fingerprint(CreateUserCommand c) {
        try {
            StringBuilder canonical = new StringBuilder();
            for (String value : new String[]{c.fullName(), c.email(), c.phoneNumber(), c.avatarUrl()})
                canonical.append(value == null ? "-1:" : value.length() + ":" + value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
