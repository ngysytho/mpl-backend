package com.example.userservice.modules.user.mapper;
import com.example.userservice.modules.user.entity.*;
import com.example.userservice.modules.user.mapper.UserPersistenceMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class UserPersistenceMapperTest {
    @Test void roundTripsAllAggregateFields() {
        var user = new User(UUID.randomUUID(), "kc-identity", "Name", "user@example.com", "+84901234567", "https://example.com/avatar.png",
                            UserStatus.SUSPENDED, Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
        var mapper = new UserPersistenceMapper(); assertThat(mapper.toDomain(mapper.toEntity(user))).isEqualTo(user);
    }
}
