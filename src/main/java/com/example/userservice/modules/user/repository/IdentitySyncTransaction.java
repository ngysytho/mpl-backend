package com.example.userservice.modules.user.repository;
import com.example.userservice.modules.user.service.IdentityManagementPort;
import com.example.userservice.modules.user.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Component
public class IdentitySyncTransaction {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final IdentityManagementPort identities;
    private final io.micrometer.core.instrument.MeterRegistry metrics;
    public IdentitySyncTransaction(JdbcTemplate jdbc, UserRepository users, IdentityManagementPort identities, io.micrometer.core.instrument.MeterRegistry metrics) {
        this.jdbc=jdbc; this.users=users; this.identities=identities; this.metrics=metrics;
    }
    @Transactional(timeout = 60)
    public void deliver(UUID id) {
        // All mutators lock the user first, preventing stale delivery and lock-order inversions.
        var lockSample=io.micrometer.core.instrument.Timer.start(metrics);
        com.example.userservice.modules.user.entity.User user;
        try { user = users.findForUpdate(id).orElseThrow(); }
        finally { lockSample.stop(metrics.timer("user.identity.sync.lock.wait")); }
        if (jdbc.queryForObject("SELECT count(*) FROM user_identity_sync WHERE user_id=?", Long.class, id) == 0) return;
        // Disable FIRST for fail-closed status changes. Enable LAST for activation/provisioning.
        if (!user.status().identityEnabled()) identities.setEnabled(user.keycloakUserId(), false);
        identities.updateProfile(user.keycloakUserId(), user.fullName());
        identities.assignRealmRole(user.keycloakUserId(), "USER");
        if (user.status().identityEnabled()) identities.setEnabled(user.keycloakUserId(), true);
        var age=jdbc.queryForObject("SELECT GREATEST(0, EXTRACT(EPOCH FROM (clock_timestamp()-created_at))) FROM user_identity_sync WHERE user_id=?", Double.class, id);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() {
                    if(age!=null) metrics.timer("user.identity.sync.latency").record(java.time.Duration.ofNanos((long)(age*1_000_000_000)));
                }
            });
        jdbc.update("DELETE FROM user_identity_sync WHERE user_id=?", id);
    }
}
