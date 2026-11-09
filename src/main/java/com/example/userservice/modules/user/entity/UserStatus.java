package com.example.userservice.modules.user.entity;
public enum UserStatus { ACTIVE, INACTIVE, SUSPENDED;
    public boolean identityEnabled() { return this == ACTIVE; }
}
