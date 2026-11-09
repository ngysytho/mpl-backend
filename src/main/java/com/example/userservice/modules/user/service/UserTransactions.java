package com.example.userservice.modules.user.service;
import com.example.userservice.modules.user.entity.*;
import com.example.userservice.modules.user.repository.UserRepository;
import com.example.userservice.modules.user.service.UserNotFoundException;
import com.example.userservice.modules.user.dto.request.UpdateProfileCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;
@Service
public class UserTransactions {
    private final UserRepository users;
    private final IdentitySyncPort sync;
    private final Clock clock;
    public UserTransactions(UserRepository users, IdentitySyncPort sync, Clock clock) { this.users = users; this.sync = sync; this.clock = clock; }
    @Transactional
    public User update(UUID id, UpdateProfileCommand command) {
        User current = users.findForUpdate(id).orElseThrow(UserNotFoundException::new);
        User saved = users.save(current.withProfile(command.fullName(), command.phoneNumber(), command.avatarUrl(), clock.instant()));
        sync.enqueue(id); return saved;
    }
    @Transactional
    public User patch(UUID id, String name, String phone, String avatar, boolean clearPhone, boolean clearAvatar) {
        User current = users.findForUpdate(id).orElseThrow(UserNotFoundException::new);
        User saved = users.save(current.withProfile(name == null ? current.fullName() : name,
            clearPhone ? null : phone == null ? current.phoneNumber() : phone,
            clearAvatar ? null : avatar == null ? current.avatarUrl() : avatar, clock.instant()));
        sync.enqueue(id); return saved;
    }
    @Transactional
    public User status(UUID id, UserStatus status) {
        User current = users.findForUpdate(id).orElseThrow(UserNotFoundException::new);
        User saved = users.save(current.withStatus(status, clock.instant()));
        sync.enqueue(id); return saved;
    }
}
