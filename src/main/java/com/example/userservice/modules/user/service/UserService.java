package com.example.userservice.modules.user.service;
import com.example.userservice.core.exception.ServiceException;
import com.example.userservice.core.response.PageResult;
import com.example.userservice.modules.user.dto.request.*;
import com.example.userservice.modules.user.service.UserNotFoundException;
import com.example.userservice.modules.user.entity.*;
import com.example.userservice.modules.user.repository.*;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Service
public class UserService {
    private final UserRepository users;
    private final ProvisioningPort provisioning;
    private final UserTransactions transactions;
    private final IdentitySyncPort sync;
    public UserService(UserRepository users, ProvisioningPort provisioning, UserTransactions transactions, IdentitySyncPort sync) {
        this.users = users; this.provisioning = provisioning; this.transactions = transactions; this.sync = sync;
    }
    // Deliberately NOT transactional: reservation, provisioning and delivery have distinct commits.
    public User create(UUID idempotencyKey, CreateUserCommand command) {
        provisioning.reserve(idempotencyKey, command);
        User user = provisioning.provision(idempotencyKey, command);
        synchronize(user.id()); return user;
    }
    public User get(UUID id) { return users.findById(id).orElseThrow(UserNotFoundException::new); }
    public User me(String subject) { return users.findByKeycloakUserId(subject).orElseThrow(UserNotFoundException::new); }
    public PageResult<User> search(UserFilter filter) { return users.search(filter); }
    public User update(UUID id, UpdateProfileCommand command) {
        User user = transactions.update(id, command); synchronize(id); return user;
    }
    public User patchMe(String subject, String name, String phone, String avatar, boolean clearPhone, boolean clearAvatar) {
        User saved = transactions.patch(me(subject).id(), name, phone, avatar, clearPhone, clearAvatar);
        synchronize(saved.id()); return saved;
    }
    public User status(UUID id, UserStatus status) {
        User user = transactions.status(id, status); synchronize(id); return user;
    }
    public void deactivate(UUID id) { status(id, UserStatus.INACTIVE); }
    private void synchronize(UUID id) {
        if (!sync.synchronize(id)) throw new ServiceException(503, "IDENTITY_SYNC_PENDING",
            "Profile state committed; identity synchronization is pending. Retry safely or check the profile.");
    }
}
