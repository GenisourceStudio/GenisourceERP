package com.genisource.erp.platform.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory index of tenant codes to ids.
 *
 * <p>Deliberately free of JPA dependencies at construction time: Hibernate asks the
 * {@link TenantIdentifierResolver} for a tenant before the first query can run, so the
 * resolver must not depend on a repository. This registry is loaded from
 * {@code ApplicationRunner} once the context is up, and refreshed whenever a tenant is
 * created or renamed.
 */
@Component
@Order(0)
public class TenantRegistry implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantRegistry.class);

    /** Sentinel meaning "context not loaded yet". Never a valid row id. */
    public static final long UNRESOLVED = -1L;

    private final Map<String, Long> idsByCode = new ConcurrentHashMap<>();
    private final SystemTenantRepository tenants;
    private final TenantProperties properties;

    private volatile long defaultTenantId = UNRESOLVED;

    public TenantRegistry(@Lazy SystemTenantRepository tenants, TenantProperties properties) {
        this.tenants = tenants;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        reload();
    }

    public void reload() {
        List<SystemTenant> all = tenants.findAll();
        idsByCode.clear();
        for (SystemTenant tenant : all) {
            idsByCode.put(tenant.getCode(), tenant.getId());
        }

        Long resolved = idsByCode.get(properties.defaultCode());
        if (resolved == null) {
            throw new IllegalStateException(
                    "Default tenant '" + properties.defaultCode() + "' is missing. "
                            + "Flyway V2 seeds it; check that migrations ran.");
        }
        defaultTenantId = resolved;
        log.info("Tenant registry loaded: {} tenant(s), default='{}' (id={})",
                idsByCode.size(), properties.defaultCode(), resolved);
    }

    /**
     * Tenant used outside a request (background jobs, login before a token exists).
     * The resolver falls back to this value, so it must never be {@code null}.
     */
    public long defaultTenantId() {
        return defaultTenantId;
    }

    public long requireId(String code) {
        Long id = idsByCode.get(code);
        if (id == null) {
            throw new IllegalArgumentException("Unknown tenant code: " + code);
        }
        return id;
    }

    public Optional<Long> findId(String code) {
        return Optional.ofNullable(idsByCode.get(code));
    }

    public void register(SystemTenant tenant) {
        idsByCode.put(tenant.getCode(), tenant.getId());
        if (tenant.getCode().equals(properties.defaultCode())) {
            defaultTenantId = tenant.getId();
        }
    }
}
