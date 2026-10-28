package com.genisource.erp.platform.admin;

import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.Codes;
import com.genisource.erp.platform.security.CurrentUser;
import com.genisource.erp.platform.security.Permission;
import com.genisource.erp.platform.security.PermissionRepository;
import com.genisource.erp.platform.security.Role;
import com.genisource.erp.platform.security.RoleRepository;
import com.genisource.erp.platform.security.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Role administration.
 *
 * <p>Roles are bundles over a permission catalogue that ships with the application, so
 * the only thing an operator can really choose is which codes a role holds. The
 * built-in ADMIN role is protected: deleting it or emptying it would strand a running
 * deployment with no way back into the console.
 */
@Service
public class RoleAdminService {

    public static final String ADMIN_ROLE = "ADMIN";

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserAccountRepository users;
    private final AuditService audit;

    public RoleAdminService(RoleRepository roles,
                            PermissionRepository permissions,
                            UserAccountRepository users,
                            AuditService audit) {
        this.roles = roles;
        this.permissions = permissions;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.RoleView> list() {
        return roles.findAll().stream()
                .map(this::view)
                .sorted(Comparator.comparing(AdminDtos.RoleView::code))
                .toList();
    }

    /** The catalogue, grouped for the role form: module → permissions. */
    @Transactional(readOnly = true)
    public List<AdminDtos.PermissionView> permissionCatalogue() {
        return permissions.findAll().stream()
                .map(p -> new AdminDtos.PermissionView(
                        p.getCode(), p.getName(), p.getModule(), p.getType().name()))
                .sorted(Comparator.comparing(AdminDtos.PermissionView::code))
                .toList();
    }

    @Transactional
    public AdminDtos.RoleView create(AdminDtos.CreateRoleRequest request) {
        String code = Codes.normalise(request.code());
        if (roles.findByCode(code).isPresent()) {
            throw ApiException.duplicateCode("Role code already exists: " + code);
        }
        Role role = new Role(code, request.name());
        role.setDescription(request.description());
        applyPermissions(role, request.permissions());
        Role saved = roles.save(role);

        audit.record("ROLE_CREATE", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse("system"),
                "ROLE", saved.getId().toString(),
                Map.of("code", saved.getCode()));
        return view(saved);
    }

    @Transactional
    public AdminDtos.RoleView update(long id, AdminDtos.UpdateRoleRequest request) {
        Role role = roles.findById(id)
                .orElseThrow(() -> ApiException.notFound("Role not found: " + id));
        role.setName(request.name());
        role.setDescription(request.description());

        if (role.isBuiltin() && ADMIN_ROLE.equals(role.getCode())
                && (request.permissions() == null || !request.permissions().contains("sys:user:write"))) {
            throw ApiException.invalidState("The ADMIN role must keep the ability to maintain users");
        }
        applyPermissions(role, request.permissions());
        Role saved = roles.save(role);

        audit.record("ROLE_UPDATE", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse("system"),
                "ROLE", saved.getId().toString(),
                Map.of("code", saved.getCode()));
        return view(saved);
    }

    @Transactional
    public void delete(long id) {
        Role role = roles.findById(id)
                .orElseThrow(() -> ApiException.notFound("Role not found: " + id));
        if (role.isBuiltin()) {
            throw ApiException.inUse("A built-in role cannot be deleted");
        }
        long holders = users.findAll().stream().filter(user -> user.getRoles().contains(role)).count();
        if (holders > 0) {
            throw ApiException.inUse("Role is still granted to " + holders + " user(s)");
        }
        roles.delete(role);

        audit.record("ROLE_DELETE", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse("system"),
                "ROLE", role.getId().toString(),
                Map.of("code", role.getCode()));
    }

    private void applyPermissions(Role role, Set<String> codes) {
        role.getPermissions().clear();
        if (codes == null || codes.isEmpty()) {
            return;
        }
        for (String code : codes) {
            Permission permission = permissions.findByCode(code)
                    .orElseThrow(() -> ApiException.badRequest("Unknown permission: " + code));
            role.grant(permission);
        }
    }

    private AdminDtos.RoleView view(Role role) {
        Set<String> codes = new TreeSet<>();
        role.getPermissions().forEach(permission -> codes.add(permission.getCode()));
        int holders = (int) users.findAll().stream().filter(user -> user.getRoles().contains(role)).count();
        return new AdminDtos.RoleView(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isBuiltin(),
                holders,
                List.copyOf(codes));
    }
}
