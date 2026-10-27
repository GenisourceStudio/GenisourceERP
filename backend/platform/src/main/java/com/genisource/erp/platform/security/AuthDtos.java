package com.genisource.erp.platform.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request and response payloads of the authentication endpoints.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * @param tenantCode only required in multi-tenant mode; ignored for private deployments
     */
    public record LoginRequest(
            @NotBlank(message = "{validation.username.required}")
            @Size(max = 64, message = "{validation.username.size}")
            String username,

            @NotBlank(message = "{validation.password.required}")
            @Size(max = 128, message = "{validation.password.size}")
            String password,

            String tenantCode) {
    }

    public record RefreshRequest(
            @NotBlank(message = "{validation.refreshToken.required}")
            String refreshToken) {
    }

    public record UserView(
            Long id,
            String username,
            String displayName,
            String tenantCode,
            List<String> roles,
            List<String> permissions,
            boolean forcePasswordChange) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            UserView user) {
    }
}
