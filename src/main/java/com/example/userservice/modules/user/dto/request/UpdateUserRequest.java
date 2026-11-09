package com.example.userservice.modules.user.dto.request;
import jakarta.validation.constraints.*;
import com.example.userservice.modules.user.dto.request.UpdateProfileCommand;
public record UpdateUserRequest(@NotBlank @Size(max=200) String fullName, @Size(max=30) String phoneNumber, @Size(max=2048) String avatarUrl) {
    public UpdateProfileCommand command() { return new UpdateProfileCommand(fullName, phoneNumber, avatarUrl); }
}
