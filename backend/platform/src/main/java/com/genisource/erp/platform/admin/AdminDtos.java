package com.genisource.erp.platform.admin;

import com.genisource.erp.platform.security.AccountStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Wire shapes for the administration screens.
 *
 * <p>Deliberately narrow: the SPA never sees a password hash, and a role is sent back
 * with the permission codes it holds rather than with entity ids, because the console
 * assigns permissions by code and the code is what an operator reads.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    // ------------------------------------------------------------------ users

    public record UserView(
            Long id,
            String username,
            String displayName,
            String email,
            String phone,
            AccountStatus status,
            boolean forcePasswordChange,
            Instant lastLoginAt,
            List<String> roles,
            long version) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+",
                    message = "Username may only contain letters, digits, dot, underscore and hyphen")
            String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 128) String displayName,
            @Size(max = 128) String email,
            @Size(max = 32) String phone,
            Set<String> roles) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 128) String displayName,
            @Size(max = 128) String email,
            @Size(max = 32) String phone,
            AccountStatus status,
            Set<String> roles) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 72) String password) {
    }

    // ------------------------------------------------------------------ roles

    public record RoleView(
            Long id,
            String code,
            String name,
            String description,
            boolean builtin,
            int userCount,
            List<String> permissions) {
    }

    public record PermissionView(String code, String name, String module, String type) {
    }

    public record CreateRoleRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 255) String description,
            Set<String> permissions) {
    }

    public record UpdateRoleRequest(
            @NotBlank @Size(max = 128) String name,
            @Size(max = 255) String description,
            Set<String> permissions) {
    }

    // ------------------------------------------------------------------ audit

    public record AuditView(
            Long id,
            String username,
            String action,
            String resourceType,
            String resourceId,
            String detail,
            String ipAddress,
            Instant createdAt) {
    }
}
