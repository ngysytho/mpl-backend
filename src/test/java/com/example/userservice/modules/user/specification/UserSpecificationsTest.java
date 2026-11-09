package com.example.userservice.modules.user.specification;
import com.example.userservice.core.specification.SafePredicates;
import com.example.userservice.modules.user.service.UserQuery;
import com.example.userservice.modules.user.specification.UserSpecifications;
import com.example.userservice.modules.user.entity.UserJpaEntity;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class UserSpecificationsTest {
    @Test void escapesWildcardCharacters() { assertThat(SafePredicates.containsPattern(" A%_! ")).isEqualTo("%a!%!_!!%"); }
    @Test void rejectsArbitrarySortPathsAndExcessivePages() {
        assertThatThrownBy(() -> UserQuery.filter(null,null,null,null,null,null,0,20,"identity.secret,asc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserQuery.filter(null,null,null,null,null,null,0,101,"email,asc")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test @SuppressWarnings("unchecked") void emptyFilterProducesAnAndPredicate() {
        Root<UserJpaEntity> root = mock(Root.class); CriteriaQuery<?> query = mock(CriteriaQuery.class); CriteriaBuilder cb = mock(CriteriaBuilder.class);
        var f = UserQuery.filter(null,null,null,null,null,null,0,20,"email,asc");
        UserSpecifications.matching(f).toPredicate(root, query, cb); verify(cb).and(any(Predicate[].class));
    }
}
