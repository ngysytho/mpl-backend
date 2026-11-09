package com.example.userservice.modules.user.entity;
import com.example.userservice.modules.user.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class UserTest {
    private User user() { return new User(UUID.randomUUID(), "kc-id", "  Nguyen Van A  ", " A@Example.COM ", null, null, UserStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH); }
    @Test void normalizesEmailAndName() { assertThat(user().email()).isEqualTo("a@example.com"); assertThat(user().fullName()).isEqualTo("Nguyen Van A"); }
    @Test void rejectsInvalidEmailAndInsecureAvatar() {
        assertThatThrownBy(() -> User.normalizeEmail("not-an-email")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user().withProfile("Name", null, "http://example.com/a", Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsBlankNameAndInvalidPhone() {
        assertThatThrownBy(() -> user().withProfile(" ", null, null, Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user().withProfile("Name", "abc", null, Instant.EPOCH)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @EnumSource(UserStatus.class)
    void everyAdminStatusTransitionPreservesIdentity(UserStatus next) {
        User result = user().withStatus(next, Instant.EPOCH.plusSeconds(1));
        assertThat(result.status()).isEqualTo(next); assertThat(result.keycloakUserId()).isEqualTo("kc-id");
        assertThat(next.identityEnabled()).isEqualTo(next == UserStatus.ACTIVE);
    }
}
