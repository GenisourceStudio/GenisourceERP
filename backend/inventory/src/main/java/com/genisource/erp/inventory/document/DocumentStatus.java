package com.genisource.erp.inventory.document;

/**
 * Lifecycle of a stock document.
 *
 * <p>{@code DRAFT} is editable and has not touched the balance. {@code POSTED} has
 * moved stock and is frozen. {@code REVERSED} was posted and then undone by a
 * red-ink document — it stays on the books so the history remains readable.
 */
public enum DocumentStatus {

    DRAFT,
    POSTED,
    REVERSED;

    public boolean isEditable() {
        return this == DRAFT;
    }
}
