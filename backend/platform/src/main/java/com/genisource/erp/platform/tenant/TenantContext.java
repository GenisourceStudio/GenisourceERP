package com.genisource.erp.platform.tenant;

import java.util.function.Supplier;

/**
 * Holds the tenant of the current request.
 *
 * <p>Two entry points set it:
 * <ul>
 *   <li>login — resolved from the submitted tenant code (multi-tenant SaaS), or from
 *       the configured default tenant (private deployment);</li>
 *   <li>every authenticated request — resolved from the {@code tid} claim of the JWT.</li>
 * </ul>
 *
 * <p>Hibernate reads this value through {@link TenantIdentifierResolver} and injects it
 * into all SQL, so business code must <strong>not</strong> filter by tenant manually.
 * The context is cleared in a servlet filter's {@code finally} block to keep pooled
 * request threads from leaking a tenant into the next request.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static boolean isPresent() {
        return CURRENT.get() != null;
    }

    /**
     * Runs an action under an explicit tenant and always restores the previous value.
     * Used by login and by background jobs, which start outside a request.
     */
    public static <T> T callWith(Long tenantId, Supplier<T> action) {
        Long previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    private static void restore(Long previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }
}
