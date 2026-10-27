package com.genisource.erp.purchase;

/**
 * The three documents that make up the buy side.
 *
 * <p>They share a table for the reason they share a screen: they are the same
 * envelope — a supplier, lines with a price, a life cycle — and what differs is
 * where in the chain they sit and what may happen next.
 */
public enum PurchaseDocType {

    /**
     * The floor asks for material. No supplier and no price yet; those are what
     * purchasing adds when it turns this into an order.
     */
    REQUISITION("QG"),

    /** A promise to buy. Supplier and price are binding. */
    ORDER("CG"),

    /**
     * Goods arrived at the door. Posting one is what moves stock and creates the
     * payable, which is why a receipt is the only one of the three that can be posted.
     */
    RECEIPT("SL");

    private final String prefix;

    PurchaseDocType(String prefix) {
        this.prefix = prefix;
    }

    /** Prefix of the generated document number, e.g. {@code CG-20261028-0001}. */
    public String prefix() {
        return prefix;
    }

    /** Only a receipt moves stock and money. */
    public boolean isPostable() {
        return this == RECEIPT;
    }

    /**
     * Whether the document needs a supplier.
     *
     * <p>A requisition is the ask, not the answer, so it may name a preferred supplier
     * or leave the choice to purchasing.
     */
    public boolean needsPartner() {
        return this != REQUISITION;
    }

    /** Whether the document needs a price: a requisition asks for a quantity, not a quote. */
    public boolean needsPrice() {
        return this != REQUISITION;
    }

    /** Whether the document has to say which warehouse the goods go to. */
    public boolean needsWarehouse() {
        return this == RECEIPT;
    }
}
