package com.example.userservice.modules.user.service;
import java.util.UUID;
public interface IdentitySyncPort {
    /** Must participate in the profile write transaction. */
    void enqueue(UUID userId);
    /** Runs after commit; returns false if desired state remains pending. */
    boolean synchronize(UUID userId);
}
