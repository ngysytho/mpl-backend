package com.example.userservice.modules.user.repository;
import com.example.userservice.core.filter.DateRange;
import com.example.userservice.modules.user.entity.UserStatus;
import java.util.Set;
public record UserFilter(String keyword, String fullName, String email, UserStatus status, DateRange created,
                        int page, int size, String sort, boolean ascending) {
    public UserFilter {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid pagination: page >= 0, size 1–100 required");
        if (!Set.of("fullName", "email", "createdAt").contains(sort)) throw new IllegalArgumentException("Unsupported sort field");
        for (String value : new String[]{keyword, fullName, email})
            if (value != null && value.length() > 254) throw new IllegalArgumentException("Filter too long");
    }
}
