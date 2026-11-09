package com.example.userservice.modules.user.mapper;
import com.example.userservice.modules.user.entity.User;
import com.example.userservice.modules.user.entity.UserJpaEntity;
import org.springframework.stereotype.Component;
@Component
public class UserPersistenceMapper {
    public User toDomain(UserJpaEntity e) {
        return new User(e.getId(), e.getKeycloakUserId(), e.getFullName(), e.getEmail(), e.getPhoneNumber(),
                        e.getAvatarUrl(), e.getStatus(), e.getCreatedAt(), e.getUpdatedAt());
    }
    public UserJpaEntity toEntity(User u) {
        return new UserJpaEntity(u.id(), u.keycloakUserId(), u.fullName(), u.email(), u.phoneNumber(), u.avatarUrl(), u.status(), u.createdAt(), u.updatedAt());
    }
}
