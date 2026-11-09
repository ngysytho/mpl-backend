package com.example.userservice.core.filter;
import java.time.Instant;
public record DateRange(Instant from, Instant to) {
    public DateRange {
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("from must precede to");
    }
}
