package com.example.userservice.core.specification;
import jakarta.persistence.criteria.*;
import java.util.Locale;
public final class SafePredicates {
    private SafePredicates() {}
    public static String containsPattern(String input) {
        return "%" + input.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
    public static Predicate contains(CriteriaBuilder cb, Expression<String> field, String input) {
        return cb.like(cb.lower(field), containsPattern(input), '!');
    }
}
