package com.stockpilot.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 200) String organizationName,
            @NotBlank @Size(min = 12, max = 128) String password) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 128) String password) {}

    public record EmailRequest(@NotBlank @Email @Size(max = 320) String email) {}
    public record TokenRequest(@NotBlank @Size(max = 128) String token) {}
    public record ResetPasswordRequest(
            @NotBlank @Size(max = 128) String token,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {}
    public record CsrfResponse(String headerName, String parameterName, String token) {}
    public record MessageResponse(String message) {}
    public record UserResponse(UUID id, String email, String name, UUID organizationId,
                               String organizationName, AuthRole role, Instant emailVerifiedAt) {}
}
