package com.example.userservice.modules.user.specification;
import com.example.userservice.core.specification.SafePredicates;
import com.example.userservice.modules.user.repository.UserFilter;
import com.example.userservice.modules.user.entity.UserJpaEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
public final class UserSpecifications {
    private UserSpecifications() {}
    public static Specification<UserJpaEntity> matching(UserFilter f) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (present(f.keyword())) predicates.add(cb.or(SafePredicates.contains(cb, root.get("fullName"), f.keyword()), SafePredicates.contains(cb, root.get("email"), f.keyword())));
            if (present(f.fullName())) predicates.add(SafePredicates.contains(cb, root.get("fullName"), f.fullName()));
            if (present(f.email())) predicates.add(SafePredicates.contains(cb, root.get("email"), f.email()));
            if (f.status() != null) predicates.add(cb.equal(root.get("status"), f.status()));
            if (f.created() != null && f.created().from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.created().from()));
            if (f.created() != null && f.created().to() != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), f.created().to()));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
    private static boolean present(String s) { return s != null && !s.isBlank(); }
}
