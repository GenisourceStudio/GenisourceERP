package com.genisource.erp.platform.security;

import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.ErrorCode;
import com.genisource.erp.platform.tenant.TenantContext;
import com.genisource.erp.platform.tenant.TenantMode;
import com.genisource.erp.platform.tenant.TenantProperties;
import com.genisource.erp.platform.tenant.TenantRegistry;
import com.nimbusds.jwt.JWTClaimsSet;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Login, refresh and profile lookup.
 *
 * <p>Login is the only place that resolves a tenant from user input: afterwards the
 * tenant always comes from the signed token, so it cannot be tampered with.
 */
@Service
public class AuthService {

    private final UserAccountRepository users;
    private final TenantRegistry tenantRegistry;
    private final TenantProperties tenantProperties;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserAccountRepository users,
                       TenantRegistry tenantRegistry,
                       TenantProperties tenantProperties,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.users = users;
        this.tenantRegistry = tenantRegistry;
        this.tenantProperties = tenantProperties;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        long tenantId = resolveTenant(request.tenantCode());

        return TenantContext.callWith(tenantId, () -> {
            UserAccount user = users.findByUsername(request.username())
                    .orElseThrow(() -> ApiException.unauthorized(
                            ErrorCode.INVALID_CREDENTIALS, "Username or password is incorrect"));

            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw ApiException.unauthorized(ErrorCode.INVALID_CREDENTIALS, "Username or password is incorrect");
            }
            if (!user.isActive()) {
                throw ApiException.unauthorized(ErrorCode.ACCOUNT_DISABLED, "Account is disabled");
            }

            user.markLoggedIn();
            users.save(user);
            return tokenResponse(toPrincipal(user));
        });
    }

    @Transactional(readOnly = true)
    public AuthDtos.TokenResponse refresh(String refreshToken) {
        JWTClaimsSet claims = jwtService.parse(refreshToken, JwtService.TYPE_REFRESH);
        AuthenticatedUser principal = jwtService.toPrincipal(claims);

        return TenantContext.callWith(principal.tenantId(), () -> {
            UserAccount user = users.findById(principal.id())
                    .orElseThrow(() -> ApiException.unauthorized(ErrorCode.TOKEN_INVALID, "Account no longer exists"));
            if (!user.isActive()) {
                throw ApiException.unauthorized(ErrorCode.ACCOUNT_DISABLED, "Account is disabled");
            }
            return tokenResponse(toPrincipal(user));
        });
    }

    private long resolveTenant(String tenantCode) {
        if (tenantProperties.mode() == TenantMode.SINGLE) {
            return tenantRegistry.defaultTenantId();
        }
        if (tenantCode == null || tenantCode.isBlank()) {
            throw ApiException.unauthorized(ErrorCode.TENANT_MISSING, "Tenant code is required");
        }
        return tenantRegistry.findId(tenantCode)
                .orElseThrow(() -> ApiException.unauthorized(ErrorCode.TENANT_NOT_FOUND, "Unknown tenant"));
    }

    private AuthDtos.TokenResponse tokenResponse(AuthenticatedUser principal) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(principal);
        JwtService.IssuedToken refresh = jwtService.issueRefreshToken(principal);
        return new AuthDtos.TokenResponse(
                access.value(),
                refresh.value(),
                access.expiresInSeconds(),
                toView(principal));
    }

    public AuthDtos.UserView toView(AuthenticatedUser principal) {
        String tenantCode = null;
        if (tenantProperties.mode() == TenantMode.SINGLE) {
            tenantCode = tenantProperties.defaultCode();
        }
        return new AuthDtos.UserView(
                principal.id(),
                principal.username(),
                principal.displayName(),
                tenantCode,
                principal.roles(),
                principal.permissions(),
                principal.forcePasswordChange());
    }

    public AuthenticatedUser toPrincipal(UserAccount user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getCode)
                .sorted()
                .toList();
        List<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .sorted()
                .toList();
        return new AuthenticatedUser(
                user.getId(),
                user.getTenantId(),
                user.getUsername(),
                user.getDisplayName(),
                roles,
                permissions,
                user.isForcePasswordChange());
    }
}
