package com.genisource.erp.platform.security;

import com.genisource.erp.platform.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

/**
 * Read-only view of the caller taken from the security context.
 *
 * <p>Exists so that services can write audit entries without every controller having to
 * pass the principal down. Authentication is a request-scoped fact, not a business input.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<Long> id() {
        return jwt().flatMap(jwt -> parseLong(jwt.getSubject()));
    }

    public static Optional<String> username() {
        return jwt().map(jwt -> jwt.getClaimAsString(JwtService.CLAIM_USERNAME));
    }

    public static Optional<Long> tenantId() {
        return jwt().map(jwt -> {
            Object claim = jwt.getClaim(JwtService.CLAIM_TENANT);
            return claim instanceof Number number ? number.longValue() : null;
        });
    }

    /** Tenant of the current request, falling back to the thread-local set by the filter. */
    public static long tenantIdOrContext() {
        return tenantId().or(() -> Optional.ofNullable(TenantContext.get())).orElse(-1L);
    }

    private static Optional<Jwt> jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return Optional.of(token.getToken());
        }
        return Optional.empty();
    }

    private static Optional<Long> parseLong(String value) {
        try {
            return value == null ? Optional.empty() : Optional.of(Long.valueOf(value));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
