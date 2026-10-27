package com.genisource.erp.inventory.lot;

/**
 * Whether a lot row tracks a batch or a single serialised unit.
 *
 * <p>Both live in {@code inv_lot} on purpose. A batch is "one row, many units"; a
 * serial is "one row, exactly one unit". Treating them as one table means the stock
 * key, the ledger and the traceability query all work the same way for both, and
 * there is only one place to look when a customer asks where a unit came from.
 */
public enum LotType {

    /** A batch: any quantity may share the lot number. */
    LOT,
    /** A serial number: exactly one unit, tracked individually. */
    SERIAL
}
