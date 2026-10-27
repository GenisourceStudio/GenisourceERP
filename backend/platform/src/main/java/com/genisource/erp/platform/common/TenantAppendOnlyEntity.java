package com.genisource.erp.platform.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.TenantId;

/**
 * A tenant-scoped row that is written once and never changed.
 *
 * <p>The inventory ledger is the example. It needs the tenant discriminator like every
 * other business table, but it has no {@code updated_at}: an entry is appended when a
 * document is posted and is never touched again, not even by a reversal — the
 * correction is a new row beside it.
 *
 * <p>"When was this last changed" would always answer the same thing here, and a
 * column that cannot vary is a column that will eventually be read as if it could.
 */
@MappedSuperclass
public abstract class TenantAppendOnlyEntity extends AppendOnlyEntity {

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
