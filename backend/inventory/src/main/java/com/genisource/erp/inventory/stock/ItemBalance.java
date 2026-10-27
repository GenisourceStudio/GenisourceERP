package com.genisource.erp.inventory.stock;

import java.math.BigDecimal;

/**
 * How much of one item the company holds, across every warehouse and bin.
 *
 * <p>MRP needs one number set per item, not a page of balance lines: it is asking "what
 * can I count on", and the answer does not depend on which shelf the units sit on. The
 * four columns mirror the four quantities {@link Stock} keeps, so a planner reading a
 * plan row can reconcile it against the stock screen line by line.
 *
 * @param onHand    units physically present
 * @param reserved  units promised to a sales order, still in the building
 * @param locked    units committed to a production order, not yet issued
 * @param inTransit units recorded as on their way but not received
 */
public record ItemBalance(Long itemId,
                          BigDecimal onHand,
                          BigDecimal reserved,
                          BigDecimal locked,
                          BigDecimal inTransit) {

    /** Whether the item holds anything at all; lets callers skip a row when nothing is there. */
    public boolean isEmpty() {
        return onHand.signum() == 0 && reserved.signum() == 0
                && locked.signum() == 0 && inTransit.signum() == 0;
    }
}
