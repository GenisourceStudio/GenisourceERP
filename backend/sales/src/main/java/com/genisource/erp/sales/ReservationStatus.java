package com.genisource.erp.sales;

/**
 * Where a promise of stock stands.
 *
 * <p>The lifecycle is short and one-way: stock is promised, then either shipped against
 * the promise or given back. There is no "partly released" because a reservation is
 * consumed line by line — a shipment takes what it takes and the rest stays promised.
 */
public enum ReservationStatus {

    /** Promised and waiting to be shipped. */
    OPEN,
    /** Shipped in full. */
    FULFILLED,
    /** Given back, because the order was cancelled or closed early. */
    RELEASED;

    /** Whether the units are still held for this order. */
    public boolean isHeld() {
        return this == OPEN;
    }
}
