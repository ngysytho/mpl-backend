package com.example.userservice.modules.user.service;

import com.example.userservice.core.exception.ServiceException;
import com.example.userservice.modules.user.dto.request.*;
import com.example.userservice.modules.user.entity.*;
import com.example.userservice.modules.user.repository.RegistrationRateLimiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {
    @Mock ProvisioningPort provisioning;
    @Mock IdentitySyncPort sync;
    @Mock RegistrationRateLimiter limiter;
    @InjectMocks UserRegistrationService service;

    private RegisterUserCommand command() {
        return new RegisterUserCommand(new CreateUserCommand("Name","new@example.com",null,null),"Example-Password-42!");
    }
    @Test void usesDedicatedReservationAndSynchronizesOnlyAfterProvisioning() {
        var key=UUID.randomUUID(); var command=command();
        var user=new User(UUID.randomUUID(),"identity","Name","new@example.com",null,null,UserStatus.ACTIVE,Instant.EPOCH,Instant.EPOCH);
        when(provisioning.provisionRegistration(key,command)).thenReturn(user); when(sync.synchronize(user.id())).thenReturn(true);
        service.register(key,command,"127.0.0.1");
        var order=inOrder(limiter,provisioning,sync);
        order.verify(limiter).check("127.0.0.1"); order.verify(provisioning).reserveRegistration(key,command);
        order.verify(provisioning).provisionRegistration(key,command); order.verify(sync).synchronize(user.id());
        verify(provisioning,never()).reserve(any(),any()); verify(provisioning,never()).provision(any(),any());
    }
    @Test void quotaRejectionPreventsAnyProvisioning() {
        doThrow(new ServiceException(429,"REGISTRATION_RATE_LIMITED","Limit")).when(limiter).check("127.0.0.1");
        assertThatThrownBy(() -> service.register(UUID.randomUUID(),command(),"127.0.0.1")).isInstanceOf(ServiceException.class);
        verifyNoInteractions(provisioning,sync);
    }
    @Test void pendingSyncIsRetryableWithoutReturningProfileInformation() {
        var key=UUID.randomUUID();var command=command();
        var user=new User(UUID.randomUUID(),"identity","Name","new@example.com",null,null,UserStatus.ACTIVE,Instant.EPOCH,Instant.EPOCH);
        when(provisioning.provisionRegistration(key,command)).thenReturn(user);
        assertThatThrownBy(() -> service.register(key,command,"127.0.0.1"))
            .isInstanceOfSatisfying(ServiceException.class,e -> {
                assertThat(e.status()).isEqualTo(503); assertThat(e.getMessage()).doesNotContain(user.id().toString(),user.email());
            });
    }
    @Test void passwordsAreRedactedAndNotSerializableFromRequest() {
        var request=new RegisterUserRequest("Name","new@example.com","Example-Password-42!",null,null);
        assertThat(request.toString()).doesNotContain(request.password());
        assertThat(request.command().toString()).doesNotContain(request.password());
        String serialized=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(request);
        assertThat(serialized).doesNotContain("password","Example-Password-42!");
    }
}
