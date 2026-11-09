package com.example.userservice.modules.user.controller;

import com.example.userservice.core.response.ApiResponse;
import com.example.userservice.modules.user.dto.request.RegisterUserRequest;
import com.example.userservice.modules.user.dto.response.RegistrationResponse;
import com.example.userservice.modules.user.service.UserRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/register")
public class UserRegistrationController {
    private final UserRegistrationService service;
    public UserRegistrationController(UserRegistrationService service) { this.service = service; }

    @PostMapping
    @SecurityRequirements
    @Operation(summary="Public registration; creates a USER identity")
    public ResponseEntity<ApiResponse<RegistrationResponse>> register(
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody RegisterUserRequest body,
            HttpServletRequest request) {
        service.register(key, body.command(), request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(ApiResponse.of(RegistrationResponse.accepted()));
    }
}
