package com.example.userservice.modules.user.repository;
import com.example.userservice.modules.user.entity.User;
import com.example.userservice.modules.user.repository.*;
import com.example.userservice.modules.user.mapper.UserPersistenceMapper;
import com.example.userservice.modules.user.specification.UserSpecifications;
import com.example.userservice.core.response.PageResult;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Repository
@Transactional(readOnly = true)
public class UserRepositoryAdapter implements UserRepository {
    private final UserJpaRepository jpa;
    private final UserPersistenceMapper mapper;
    public UserRepositoryAdapter(UserJpaRepository jpa, UserPersistenceMapper mapper) { this.jpa=jpa; this.mapper=mapper; }
    public Optional<User> findById(UUID id) { return jpa.findById(id).map(mapper::toDomain); }
    public Optional<User> findByKeycloakUserId(String subject) { return jpa.findByKeycloakUserId(subject).map(mapper::toDomain); }
    @Transactional public Optional<User> findForUpdate(UUID id) { return jpa.findForUpdate(id).map(mapper::toDomain); }
    public boolean existsByEmail(String email) { return jpa.existsByEmail(email); }
    @Transactional
    public User save(User user) {
        var existing = jpa.findById(user.id());
        var entity = existing.orElseGet(() -> mapper.toEntity(user));
        if (existing.isPresent()) entity.update(user.fullName(), user.phoneNumber(), user.avatarUrl(), user.status(), user.updatedAt());
        return mapper.toDomain(jpa.saveAndFlush(entity));
    }
    public PageResult<User> search(UserFilter filter) {
        Sort sort = Sort.by(filter.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC, filter.sort()).and(Sort.by("id"));
        var page = jpa.findAll(UserSpecifications.matching(filter), PageRequest.of(filter.page(), filter.size(), sort));
        return new PageResult<>(page.map(mapper::toDomain).getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
