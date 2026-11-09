package com.example.userservice.modules.user.dto.request;
import com.example.userservice.modules.user.entity.UserStatus;
import jakarta.validation.constraints.NotNull;
public record ChangeStatusRequest(@NotNull UserStatus status) {}
