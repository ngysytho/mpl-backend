package com.example.userservice.modules.user.repository;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserSyncMetrics {
    public UserSyncMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        registry.gauge("user.identity.sync.pending", jdbc, db -> db.queryForObject("SELECT count(*) FROM user_identity_sync", Long.class));
        registry.gauge("user.identity.provisioning.incomplete", jdbc,
            db -> db.queryForObject("SELECT count(*) FROM user_provisioning_requests WHERE user_id IS NULL", Long.class));
        registry.gauge("user.identity.sync.retry.attempts", jdbc,
            db -> db.queryForObject("SELECT COALESCE(sum(attempts),0) FROM user_identity_sync", Double.class));
        registry.gauge("user.identity.sync.oldest.seconds", jdbc,
            db -> db.queryForObject("SELECT COALESCE(GREATEST(0, EXTRACT(EPOCH FROM (clock_timestamp()-min(created_at)))),0) FROM user_identity_sync", Double.class));
    }
}
