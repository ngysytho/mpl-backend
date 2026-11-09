package com.example.userservice.modules.user.repository;
import com.example.userservice.modules.user.entity.User;
import com.example.userservice.core.response.PageResult;
import java.util.Optional;
import java.util.UUID;
public interface UserRepository {
    Optional<User> findById(UUID id);
    Optional<User> findByKeycloakUserId(String subject);
    Optional<User> findForUpdate(UUID id);
    boolean existsByEmail(String email);
    User save(User user);
    PageResult<User> search(UserFilter filter);
}
