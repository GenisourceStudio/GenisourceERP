package com.genisource.erp.platform.security;

import java.util.List;
import java.util.Set;

/**
 * Immutable snapshot of the signed-in principal, carried in the security context and
 * embedded into the token claims.
 */
public record AuthenticatedUser(
        Long id,
        Long tenantId,
        String username,
        String displayName,
        List<String> roles,
        List<String> permissions,
        boolean forcePasswordChange) {

    public Set<String> permissionSet() {
        return Set.copyOf(permissions);
    }
}
