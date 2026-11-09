package com.example.userservice.modules.user.service;
import com.example.userservice.modules.user.dto.request.*;
import com.example.userservice.modules.user.service.*;
import com.example.userservice.modules.user.entity.*;
import com.example.userservice.modules.user.repository.UserRepository;
import com.example.userservice.core.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository users; @Mock ProvisioningPort provisioning; @Mock UserTransactions transactions; @Mock IdentitySyncPort sync;
    @InjectMocks UserService service;
    private User user() { return new User(UUID.randomUUID(), "verified-subject", "Name", "a@example.com", null, null, UserStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH); }
    @Test void reservesBeforeProvisioningAndDeliversAfterwards() {
        var key = UUID.randomUUID(); var command = new CreateUserCommand("Name", "a@example.com", null, null); var user = user();
        when(provisioning.provision(key, command)).thenReturn(user); when(sync.synchronize(user.id())).thenReturn(true);
        assertThat(service.create(key, command)).isEqualTo(user);
        var order = inOrder(provisioning, sync); order.verify(provisioning).reserve(key, command);
        order.verify(provisioning).provision(key, command); order.verify(sync).synchronize(user.id());
    }
    @Test void synchronizationFailureSignalsDurablyCommittedState() {
        var user = user(); when(transactions.status(user.id(), UserStatus.SUSPENDED)).thenReturn(user.withStatus(UserStatus.SUSPENDED, Instant.EPOCH));
        when(sync.synchronize(user.id())).thenReturn(false);
        assertThatThrownBy(() -> service.status(user.id(), UserStatus.SUSPENDED)).isInstanceOfSatisfying(ServiceException.class,
            e -> { assertThat(e.status()).isEqualTo(503); assertThat(e.code()).isEqualTo("IDENTITY_SYNC_PENDING"); });
    }
    @Test void selfProfileUsesVerifiedSubject() {
        var user = user(); when(users.findByKeycloakUserId("verified-subject")).thenReturn(Optional.of(user));
        assertThat(service.me("verified-subject")).isEqualTo(user); verify(users, never()).findById(any());
    }
    @Test void deleteDeactivatesInsteadOfErasing() {
        var user = user(); when(transactions.status(user.id(), UserStatus.INACTIVE)).thenReturn(user); when(sync.synchronize(user.id())).thenReturn(true);
        service.deactivate(user.id()); verify(transactions).status(user.id(), UserStatus.INACTIVE);
    }
}
