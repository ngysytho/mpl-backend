package com.example.userservice.modules.user.dto.request;
import jakarta.validation.constraints.*;
public record PatchMeRequest(@Size(min=1, max=200) String fullName, @Size(max=30) String phoneNumber,
                             @Size(max=2048) String avatarUrl, boolean clearPhoneNumber, boolean clearAvatarUrl) {
    @AssertTrue(message="Cannot set and clear the same field")
    public boolean isConsistent() { return !(clearPhoneNumber && phoneNumber != null) && !(clearAvatarUrl && avatarUrl != null); }
}
