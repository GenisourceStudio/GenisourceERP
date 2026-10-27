package com.genisource.erp.sales;

/**
 * The three documents that make up the sell side.
 *
 * <p>The mirror image of the buy side, and for the same reason: a quotation, an order
 * and a shipment are one envelope with three lives. What differs is what each one
 * commits.
 */
public enum SalesDocType {

    /** A price offered. Nothing is committed and no stock is touched. */
    QUOTATION("BJ"),

    /**
     * A promise to deliver. Confirming one reserves stock: the units are spoken for, so
     * a second order cannot sell them.
     */
    ORDER("XS"),

    /**
     * Goods leaving. Posting one releases the reservation, issues the stock and raises
     * the receivable, which is why a shipment is the only one of the three that can be
     * posted.
     */
    SHIPMENT("FH");

    private final String prefix;

    SalesDocType(String prefix) {
        this.prefix = prefix;
    }

    /** Prefix of the generated document number, e.g. {@code XS-20261028-0001}. */
    public String prefix() {
        return prefix;
    }

    /** Only a shipment moves stock and money. */
    public boolean isPostable() {
        return this == SHIPMENT;
    }

    /** A quotation may be sent to a prospect; an order and a shipment need a customer. */
    public boolean needsPartner() {
        return this != QUOTATION;
    }

    /** Whether the document has to say which warehouse the goods come from. */
    public boolean needsWarehouse() {
        return this == SHIPMENT;
    }

    /** Whether confirming the document promises stock to the customer. */
    public boolean reservesStock() {
        return this == ORDER;
    }
}
