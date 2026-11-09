package com.example.userservice.modules.user.service;
import java.util.*;
public interface IdentityManagementPort {
    record Identity(String id, String username, String email, String fullName, boolean enabled, Map<String, List<String>> attributes) {}
    String createDisabled(String email, String fullName, UUID provisioningId);
    String createDisabledWithPassword(String email, String fullName, UUID provisioningId, String password);
    Optional<Identity> findById(String id);
    Optional<Identity> findByExactEmail(String email);
    Optional<Identity> findByExactUsername(String username);
    void updateProfile(String id, String fullName);
    void setEnabled(String id, boolean enabled);
    void assignRealmRole(String id, String role);
    void removeRealmRole(String id, String role);
    void assignClientRole(String id, String clientId, String role);
    void removeClientRole(String id, String clientId, String role);
    void sendActions(String id, List<String> actions);
    // Not used by HTTP DELETE: explicit identity erasure is an offline, reviewed operation.
    void deleteOwnedIdentity(String id, UUID provisioningId);
}
