package com.genisource.erp.platform.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

/**
 * Base class for every table that belongs to a tenant.
 *
 * <p>Isolation model is <strong>shared schema + row-level discriminator</strong>
 * (see ADR-0002). Hibernate injects {@code tenant_id} into every generated SQL
 * statement, so a missing {@code WHERE tenant_id = ?} is a framework bug rather than
 * an application bug. Private deployments run with a single fixed tenant, which makes
 * the discriminator a constant — the column is still present so both deployment
 * shapes share one code path.
 */
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
