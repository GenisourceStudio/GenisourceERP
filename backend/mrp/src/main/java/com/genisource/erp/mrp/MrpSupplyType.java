package com.genisource.erp.mrp;

/**
 * Which way a plan row says to obtain the item.
 *
 * <p>Two values, not three. An item that is neither makeable nor purchasable produces a
 * shortage with no plan row at all — inventing a "cannot obtain" supply type would put a
 * row on the plan screen that no button can act on.
 */
public enum MrpSupplyType {

    /** Buy it: the item has no released bill of materials but is marked as purchased. */
    PURCHASE,

    /** Make it: a released bill of materials exists, so the shop floor can build it. */
    PRODUCTION;

    public String label() {
        return this == PURCHASE ? "purchase" : "production";
    }
}
