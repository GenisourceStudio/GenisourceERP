package com.genisource.erp.platform.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Bridges {@link TenantContext} into Hibernate.
 *
 * <p>Hibernate asks for the tenant while building every statement, including inserts,
 * so the value must never be {@code null} — a null would let the statement run without
 * the discriminator. Before the registry has loaded (i.e. during schema validation) we
 * return a sentinel that matches no row, which is harmless because no business table is
 * queried at that point.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<Long> {

    private final TenantRegistry registry;

    public TenantIdentifierResolver(TenantRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Long resolveCurrentTenantIdentifier() {
        Long current = TenantContext.get();
        if (current != null) {
            return current;
        }
        return registry.defaultTenantId();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
