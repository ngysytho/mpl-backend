package com.example.userservice.modules.user.entity;
import com.example.userservice.modules.user.entity.UserStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "users")
public class UserJpaEntity {
    @Id private UUID id;
    @Column(name="keycloak_user_id", nullable=false, unique=true, length=255) private String keycloakUserId;
    @Column(name="full_name", nullable=false, length=200) private String fullName;
    @Column(nullable=false, unique=true, length=254) private String email;
    @Column(name="phone_number", length=30) private String phoneNumber;
    @Column(name="avatar_url", length=2048) private String avatarUrl;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private UserStatus status;
    @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version private Long version;
    protected UserJpaEntity() {}
    public UserJpaEntity(UUID id, String keycloakUserId, String fullName, String email, String phoneNumber,
                    String avatarUrl, UserStatus status, Instant createdAt, Instant updatedAt) {
        this.id=id; this.keycloakUserId=keycloakUserId; this.fullName=fullName; this.email=email;
        this.phoneNumber=phoneNumber; this.avatarUrl=avatarUrl; this.status=status; this.createdAt=createdAt; this.updatedAt=updatedAt;
    }

    public void update(String name, String phone, String avatar, UserStatus status, Instant now) {
        this.fullName=name; this.phoneNumber=phone; this.avatarUrl=avatar; this.status=status; this.updatedAt=now;
    }
    public UUID getId() { return id; }
    public String getKeycloakUserId() { return keycloakUserId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getAvatarUrl() { return avatarUrl; }
    public UserStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
