package com.example.userservice.modules.user.repository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class IdentitySyncMetricsTest {
    @Test void failureAndDatabaseBackoffFailureStayRetryableAndAreTimed() {
        var jdbc=mock(JdbcTemplate.class);var delivery=mock(IdentitySyncTransaction.class);var metrics=new SimpleMeterRegistry();
        var sync=new IdentitySyncAdapter(jdbc,delivery,true,metrics);var id=UUID.randomUUID();
        doThrow(new IllegalStateException()).when(delivery).deliver(id);
        when(jdbc.update(anyString(),eq(id))).thenThrow(new IllegalStateException());
        assertThat(sync.synchronize(id)).isFalse();
        assertThat(metrics.get("user.identity.sync.attempts").counter().count()).isEqualTo(1);
        assertThat(metrics.get("user.identity.sync.failures").counter().count()).isEqualTo(1);
        assertThat(metrics.get("user.identity.sync.delivery.duration").timer().count()).isEqualTo(1);
    }
    @Test void disabledWorkerDoesNotPollOrDeliver() {
        var jdbc=mock(JdbcTemplate.class);var delivery=mock(IdentitySyncTransaction.class);
        new IdentitySyncAdapter(jdbc,delivery,false,new SimpleMeterRegistry()).reconcile();
        verifyNoInteractions(jdbc,delivery);
    }
}
