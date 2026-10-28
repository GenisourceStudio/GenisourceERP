package com.genisource.erp.platform.admin;

import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.PageResponse;
import com.genisource.erp.platform.security.AccountStatus;
import com.genisource.erp.platform.security.CurrentUser;
import com.genisource.erp.platform.security.Role;
import com.genisource.erp.platform.security.RoleRepository;
import com.genisource.erp.platform.security.UserAccount;
import com.genisource.erp.platform.security.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User administration: create, edit, disable and reset credentials.
 *
 * <p>Two guards matter more than the CRUD. A user cannot disable their own account —
 * the console has no "undo" for locking yourself out. And the tenant must always keep
 * at least one enabled account that holds the built-in ADMIN role; losing it means
 * nobody can administer anything again, which is only recoverable from the database.
 */
@Service
public class UserAdminService {

    public static final String ADMIN_ROLE = "ADMIN";

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public UserAdminService(UserAccountRepository users,
                            RoleRepository roles,
                            PasswordEncoder passwordEncoder,
                            AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.UserView> list(String keyword, AccountStatus status, int page, int size) {
        // Filtering belongs in the query, not after it. Filtering a fetched page leaves
        // `total` describing the unfiltered table, so the pager promises rows that the
        // filter has already removed.
        List<Specification<UserAccount>> parts = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            parts.add((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("username")), like),
                    builder.like(builder.lower(root.get("displayName")), like),
                    builder.like(builder.lower(root.get("email")), like)));
        }
        if (status != null) {
            parts.add((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        Specification<UserAccount> where = parts.stream().reduce(Specification::and).orElse(null);

        Page<UserAccount> result = users.findAll(where,
                PageRequest.of(page, size, Sort.by("username").ascending()));
        return PageResponse.of(result, this::view);
    }

    @Transactional(readOnly = true)
    public AdminDtos.UserView get(long id) {
        return view(find(id));
    }

    @Transactional
    public AdminDtos.UserView create(AdminDtos.CreateUserRequest request) {
        if (users.existsByUsername(request.username())) {
            throw ApiException.duplicateCode("Username already exists: " + request.username());
        }
        UserAccount user = new UserAccount(request.username(),
                passwordEncoder.encode(request.password()),
                request.displayName());
        user.setEmail(blankToNull(request.email()));
        user.setPhone(blankToNull(request.phone()));
        applyRoles(user, request.roles());
        UserAccount saved = users.save(user);

        audit.record("USER_CREATE", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse("system"),
                "USER", saved.getId().toString(),
                Map.of("username", saved.getUsername(), "roles", roleCodes(saved)));
        return view(saved);
    }

    @Transactional
    public AdminDtos.UserView update(long id, AdminDtos.UpdateUserRequest request) {
        UserAccount user = find(id);
        user.setDisplayName(request.displayName());
        user.setEmail(blankToNull(request.email()));
        user.setPhone(blankToNull(request.phone()));

        if (request.status() != null && request.status() != user.getStatus()) {
            if (isSelf(user)) {
                throw ApiException.invalidState("You cannot change the status of your own account");
            }
            if (request.status() != AccountStatus.ACTIVE && wouldLoseLastAdmin(user)) {
                throw ApiException.invalidState("At least one enabled account must keep the ADMIN role");
            }
            user.setStatus(request.status());
        }
        if (request.roles() != null) {
            if (isSelf(user) && !request.roles().contains(ADMIN_ROLE) && wouldLoseLastAdmin(user)) {
                throw ApiException.invalidState("At least one enabled account must keep the ADMIN role");
            }
            applyRoles(user, request.roles());
        }
        UserAccount saved = users.save(user);

        audit.record("USER_UPDATE", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse("system"),
                "USER", saved.getId().toString(),
                Map.of("username", saved.getUsername(), "roles", roleCodes(saved)));
        return view(saved);
    }

    @Transactional
    public void resetPassword(long id, AdminDtos.ResetPasswordRequest request) {
        UserAccount user = find(id);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setForcePasswordChange(true);
        users.save(user);

        audit.record("USER_RESET_PASSWORD", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse("system"),
                "USER", user.getId().toString(),
                Map.of("username", user.getUsername()));
    }

    private UserAccount find(long id) {
        return users.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found: " + id));
    }

    private void applyRoles(UserAccount user, Set<String> codes) {
        user.getRoles().clear();
        if (codes == null || codes.isEmpty()) {
            return;
        }
        for (String code : codes) {
            Role role = roles.findByCode(code)
                    .orElseThrow(() -> ApiException.badRequest("Unknown role: " + code));
            user.addRole(role);
        }
    }

    private boolean isSelf(UserAccount user) {
        return CurrentUser.id().isPresent() && CurrentUser.id().get().equals(user.getId());
    }

    /**
     * True when disabling {@code candidate} — or stripping its ADMIN role — would leave
     * the tenant with no enabled account holding ADMIN.
     */
    private boolean wouldLoseLastAdmin(UserAccount candidate) {
        boolean candidateIsAdmin = roleCodes(candidate).contains(ADMIN_ROLE);
        if (!candidateIsAdmin) {
            return false;
        }
        return users.findAll().stream()
                .filter(UserAccount::isActive)
                .filter(other -> !other.getId().equals(candidate.getId()))
                .noneMatch(other -> roleCodes(other).contains(ADMIN_ROLE));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static Set<String> roleCodes(UserAccount user) {
        Set<String> codes = new LinkedHashSet<>();
        user.getRoles().forEach(role -> codes.add(role.getCode()));
        return codes;
    }

    private AdminDtos.UserView view(UserAccount user) {
        return new AdminDtos.UserView(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                user.isForcePasswordChange(),
                user.getLastLoginAt(),
                List.copyOf(roleCodes(user)),
                user.getVersion());
    }
}
