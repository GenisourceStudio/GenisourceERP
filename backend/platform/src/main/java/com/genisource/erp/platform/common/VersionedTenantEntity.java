package com.genisource.erp.platform.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

/**
 * Tenant-owned row that people edit through the console, hence needs optimistic locking.
 *
 * <p>Two users opening the same item form and saving is a normal working day in a
 * factory, so every mutable master-data aggregate carries a version. The column is
 * called {@code row_version} rather than {@code version} because BOM and routing rows
 * already have a domain-level {@code version} (the engineering revision label), and two
 * different meanings for one word in one table is how silent bugs are born.
 *
 * <p>Documents (purchase orders, production orders) will use a different concurrency
 * model — state machines with explicit transitions — so they do not extend this class.
 */
@MappedSuperclass
public abstract class VersionedTenantEntity extends TenantScopedEntity {

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    public long getRowVersion() {
        return rowVersion;
    }
}
