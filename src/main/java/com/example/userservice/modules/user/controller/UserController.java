package com.example.userservice.modules.user.controller;
import com.example.userservice.core.audit.AuditLog;
import com.example.userservice.core.response.PageResult;
import com.example.userservice.core.response.ApiResponse;
import com.example.userservice.modules.user.dto.request.*;
import com.example.userservice.modules.user.dto.response.UserResponse;
import com.example.userservice.modules.user.service.UserQuery;
import com.example.userservice.modules.user.service.UserService;
import com.example.userservice.modules.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;
    private final AuditLog audit;
    public UserController(UserService service, AuditLog audit) { this.service=service; this.audit=audit; }
    @PostMapping
    @Operation(summary="Provision user; retry the identical body with the same UUID Idempotency-Key")
    public ResponseEntity<ApiResponse<UserResponse>> create(@RequestHeader("Idempotency-Key") UUID key,
                                                            @Valid @RequestBody CreateUserRequest body, @AuthenticationPrincipal Jwt jwt) {
        audit.record("USER_CREATE_REQUESTED", jwt.getSubject(), key);
        var user = service.create(key, body.command()); audit.record("USER_CREATED_OR_REPLAYED", jwt.getSubject(), user.id());
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(ApiResponse.of(UserResponse.from(user)));
    }
    @GetMapping
    public ApiResponse<PageResult<UserResponse>> list(@RequestParam(required=false) String keyword,
        @RequestParam(required=false) String fullName, @RequestParam(required=false) String email,
        @RequestParam(required=false) UserStatus status, @RequestParam(required=false) Instant from,
        @RequestParam(required=false) Instant to, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="createdAt,desc") String sort) {
        var results = service.search(UserQuery.filter(keyword, fullName, email, status, from, to, page, size, sort));
        return ApiResponse.of(new PageResult<>(results.content().stream().map(UserResponse::from).toList(),
            results.page(), results.size(), results.totalElements(), results.totalPages()));
    }
    @GetMapping("/{id}") public ApiResponse<UserResponse> get(@PathVariable UUID id) { return ApiResponse.of(UserResponse.from(service.get(id))); }
    @PutMapping("/{id}")
    @Operation(summary="Replace mutable profile fields; email and identity ID are immutable")
    public ApiResponse<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest body, @AuthenticationPrincipal Jwt jwt) {
        audit.record("USER_UPDATE_REQUESTED", jwt.getSubject(), id);
        var user = service.update(id, body.command()); audit.record("USER_UPDATED", jwt.getSubject(), id); return ApiResponse.of(UserResponse.from(user));
    }
    @PatchMapping("/{id}/status")
    public ApiResponse<UserResponse> status(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest body, @AuthenticationPrincipal Jwt jwt) {
        audit.record("USER_STATUS_CHANGE_REQUESTED", jwt.getSubject(), id);
        var user = service.status(id, body.status()); audit.record("USER_STATUS_" + body.status(), jwt.getSubject(), id); return ApiResponse.of(UserResponse.from(user));
    }
    @DeleteMapping("/{id}")
    @Operation(summary="Deactivate profile and disable identity; no physical deletion")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        audit.record("USER_DEACTIVATION_REQUESTED", jwt.getSubject(), id);
        service.deactivate(id); audit.record("USER_DEACTIVATED", jwt.getSubject(), id); return ResponseEntity.noContent().build();
    }
    @GetMapping("/me") public ApiResponse<UserResponse> me(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.of(UserResponse.from(service.me(jwt.getSubject()))); }
    @PatchMapping("/me")
    public ApiResponse<UserResponse> patchMe(@Valid @RequestBody PatchMeRequest body, @AuthenticationPrincipal Jwt jwt) {
        var user = service.patchMe(jwt.getSubject(), body.fullName(), body.phoneNumber(), body.avatarUrl(), body.clearPhoneNumber(), body.clearAvatarUrl());
        audit.record("USER_SELF_UPDATED", jwt.getSubject(), user.id()); return ApiResponse.of(UserResponse.from(user));
    }
}
