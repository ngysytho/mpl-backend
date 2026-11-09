package com.example.userservice.modules.user.service;
import com.example.userservice.core.filter.DateRange;
import com.example.userservice.modules.user.entity.UserStatus;
import com.example.userservice.modules.user.repository.UserFilter;
import java.time.Instant;
public final class UserQuery {
    private UserQuery() {}
    public static UserFilter filter(String keyword, String fullName, String email, UserStatus status,
                                    Instant from, Instant to, int page, int size, String sort) {
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !(parts[1].equals("asc") || parts[1].equals("desc")))
            throw new IllegalArgumentException("Sort must be field,asc or field,desc");
        return new UserFilter(keyword, fullName, email, status, new DateRange(from, to), page, size, parts[0], parts[1].equals("asc"));
    }
}
