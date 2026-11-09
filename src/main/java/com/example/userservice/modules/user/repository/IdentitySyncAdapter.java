package com.example.userservice.modules.user.repository;
import com.example.userservice.modules.user.service.IdentitySyncPort;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.UUID;
@Component
public class IdentitySyncAdapter implements IdentitySyncPort {
    private static final Logger log = LoggerFactory.getLogger(IdentitySyncAdapter.class);
    private final JdbcTemplate jdbc;
    private final IdentitySyncTransaction delivery;
    private final boolean enabled;
    private final io.micrometer.core.instrument.MeterRegistry metrics;
    public IdentitySyncAdapter(JdbcTemplate jdbc, IdentitySyncTransaction delivery, @Value("${app.sync.enabled:true}") boolean enabled, io.micrometer.core.instrument.MeterRegistry metrics) {
        this.jdbc=jdbc; this.delivery=delivery; this.enabled=enabled; this.metrics=metrics;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(UUID id) {
        jdbc.update("INSERT INTO user_identity_sync(user_id) VALUES (?) ON CONFLICT(user_id) DO UPDATE SET available_at=now(), attempts=0", id);
    }
    public boolean synchronize(UUID id) {
        var sample=io.micrometer.core.instrument.Timer.start(metrics);
        metrics.counter("user.identity.sync.attempts").increment();
        try { delivery.deliver(id); return true; }
        catch (RuntimeException failure) {
            metrics.counter("user.identity.sync.failures").increment();
            log.warn("Identity synchronization pending for userId={}, failureType={}", id, failure.getClass().getSimpleName());
            try {
                jdbc.update("UPDATE user_identity_sync SET attempts=least(attempts+1,1000000), available_at=now() + least(300, 5 * power(2, least(attempts, 6))) * interval '1 second' WHERE user_id=?", id);
            } catch (RuntimeException dbFailure) {
                log.error("Could not record synchronization backoff for userId={}", id);
            }
            return false;
        } finally {
            // Includes transaction commit and waiting for the row lock; no user IDs in metric tags.
            sample.stop(metrics.timer("user.identity.sync.delivery.duration"));
        }
    }
    @Scheduled(fixedDelayString = "${app.sync.delay:10000}")
    public void reconcile() {
        if (!enabled) return;
        try {
            var ids = jdbc.query("SELECT user_id FROM user_identity_sync WHERE available_at<=now() ORDER BY available_at LIMIT 50",
                                 (rs, n) -> rs.getObject(1, UUID.class));
            ids.forEach(this::synchronize);
        } catch (RuntimeException failure) {
            log.error("Identity reconciliation unavailable, failureType={}", failure.getClass().getSimpleName());
        }
    }
}
