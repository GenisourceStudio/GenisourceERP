package com.genisource.erp.platform.common;

/**
 * Revision lifecycle shared by bills of materials and routings.
 *
 * <p>{@code DRAFT} is editable and invisible to planning; {@code RELEASED} is frozen and
 * is the only state MRP expands; {@code OBSOLETE} is frozen and kept for traceability —
 * an order released last year must still explain what it was built from.
 */
public enum RevisionStatus {
    DRAFT,
    RELEASED,
    OBSOLETE;

    public boolean isEditable() {
        return this == DRAFT;
    }
}
