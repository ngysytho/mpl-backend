package com.example.userservice.modules.user.service;
import com.example.userservice.core.exception.ServiceException;
public final class UserNotFoundException extends ServiceException {
    public UserNotFoundException() { super(404, "USER_NOT_FOUND", "User profile not found"); }
}
