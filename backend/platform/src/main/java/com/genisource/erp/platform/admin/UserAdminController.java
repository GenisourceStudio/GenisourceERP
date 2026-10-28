package com.genisource.erp.platform.admin;

import com.genisource.erp.platform.common.PageResponse;
import com.genisource.erp.platform.security.AccountStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Users and roles — one controller because they are one screen. A user row is meaningless
 * without the roles it can be granted, and a role is meaningless without the users
 * holding it.
 */
@RestController
@RequestMapping("/api/system")
@Tag(name = "System :: users and roles", description = "Account and role administration")
public class UserAdminController {

    private final UserAdminService users;
    private final RoleAdminService roles;

    public UserAdminController(UserAdminService users, RoleAdminService roles) {
        this.users = users;
        this.roles = roles;
    }

    // ----------------------------------------------------------------- users

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('sys:user:read')")
    @Operation(summary = "Page through accounts of the current tenant")
    public PageResponse<AdminDtos.UserView> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return users.list(q, status, page, size);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('sys:user:read')")
    public AdminDtos.UserView getUser(@PathVariable long id) {
        return users.get(id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sys:user:write')")
    public AdminDtos.UserView createUser(@Valid @RequestBody AdminDtos.CreateUserRequest request) {
        return users.create(request);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('sys:user:write')")
    public AdminDtos.UserView updateUser(@PathVariable long id,
                                         @Valid @RequestBody AdminDtos.UpdateUserRequest request) {
        return users.update(id, request);
    }

    @PostMapping("/users/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('sys:user:write')")
    @Operation(summary = "Set a new password; the account must change it at next sign-in")
    public void resetPassword(@PathVariable long id,
                              @Valid @RequestBody AdminDtos.ResetPasswordRequest request) {
        users.resetPassword(id, request);
    }

    // ----------------------------------------------------------------- roles

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('sys:role:read')")
    public List<AdminDtos.RoleView> listRoles() {
        return roles.list();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('sys:role:read')")
    @Operation(summary = "The permission catalogue a role may be granted")
    public List<AdminDtos.PermissionView> listPermissions() {
        return roles.permissionCatalogue();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sys:role:write')")
    public AdminDtos.RoleView createRole(@Valid @RequestBody AdminDtos.CreateRoleRequest request) {
        return roles.create(request);
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('sys:role:write')")
    public AdminDtos.RoleView updateRole(@PathVariable long id,
                                         @Valid @RequestBody AdminDtos.UpdateRoleRequest request) {
        return roles.update(id, request);
    }

    @PostMapping("/roles/{id}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('sys:role:write')")
    @Operation(summary = "Delete a custom role that nobody holds")
    public void deleteRole(@PathVariable long id) {
        roles.delete(id);
    }
}
