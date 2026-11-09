package com.example.userservice.modules.user.service;
import com.example.userservice.modules.user.dto.request.CreateUserCommand;
import com.example.userservice.modules.user.dto.request.RegisterUserCommand;
import com.example.userservice.modules.user.entity.User;
import java.util.UUID;
public interface ProvisioningPort {
    void reserve(UUID key, CreateUserCommand command);
    User provision(UUID key, CreateUserCommand command);
    void reserveRegistration(UUID key, RegisterUserCommand command);
    User provisionRegistration(UUID key, RegisterUserCommand command);
}
