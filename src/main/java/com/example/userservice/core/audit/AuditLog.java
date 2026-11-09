package com.example.userservice.core.audit;
import org.slf4j.*;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class AuditLog {
    private static final Logger log = LoggerFactory.getLogger("AUDIT");
    public void record(String action, String actor, UUID target) {
        // Subject is verified by Spring Security. Strip CR/LF to preserve log record boundaries.
        log.info("action={} actor={} target={}", action, actor.replaceAll("[\r\n]", ""), target);
    }
}
