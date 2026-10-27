package com.genisource.erp.platform.common;

/**
 * Lifecycle of a master-data record (item, category, partner, work centre).
 *
 * <p>Rows are never deleted: history and documents have to keep pointing at them. What a
 * factory calls "deleting" a material is deactivation, which hides it from pickers but
 * keeps every past transaction readable.
 */
public enum RecordStatus {
    ACTIVE,
    INACTIVE;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
