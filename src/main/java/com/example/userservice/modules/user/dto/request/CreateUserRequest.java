package com.example.userservice.modules.user.dto.request;
import jakarta.validation.constraints.*;
import com.example.userservice.modules.user.dto.request.CreateUserCommand;
public record CreateUserRequest(@NotBlank @Size(max=200) String fullName, @NotBlank @Email @Size(max=254) String email,
                               @Size(max=30) String phoneNumber, @Size(max=2048) String avatarUrl) {
    public CreateUserCommand command() { return new CreateUserCommand(fullName, email, phoneNumber, avatarUrl); }
}
