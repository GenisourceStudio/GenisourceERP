package com.genisource.erp.platform.tenant;

/**
 * How the instance serves tenants.
 *
 * <ul>
 *   <li>{@link #SINGLE} — one factory, private deployment. Every row still carries
 *       {@code tenant_id}, pinned to the configured default tenant.</li>
 *   <li>{@link #MULTI} — SaaS. A request's tenant travels in the JWT {@code tid} claim
 *       and is chosen at login time.</li>
 * </ul>
 */
public enum TenantMode {
    SINGLE,
    MULTI
}
