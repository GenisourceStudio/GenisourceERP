package com.genisource.erp.inventory.document;

/**
 * The kind of stock document.
 *
 * <p>All four are the same envelope — a warehouse, some lines, a posting — so they
 * share one table and one posting routine. What differs is how a line is interpreted:
 * a receipt adds stock at its own cost, an issue removes stock at the current
 * average, a transfer moves stock between two places without changing its value, and
 * a count compares the book against the shelf.
 */
public enum DocumentType {

    /** Goods arriving: purchase receipt, production receipt, return. */
    INBOUND("RK"),
    /** Goods leaving: sales issue, production issue, scrapping. */
    OUTBOUND("CK"),
    /** Moving stock from one warehouse to another. */
    TRANSFER("YK"),
    /** A physical count that adjusts the book to what was found. */
    COUNT("PD");

    private final String prefix;

    DocumentType(String prefix) {
        this.prefix = prefix;
    }

    /** Prefix of the generated document number, e.g. {@code RK-20261027-0001}. */
    public String prefix() {
        return prefix;
    }

    /** A transfer needs a destination; the other types do not. */
    public boolean needsDestination() {
        return this == TRANSFER;
    }

    /** A count compares against the book; the others state an absolute quantity. */
    public boolean isCount() {
        return this == COUNT;
    }
}
