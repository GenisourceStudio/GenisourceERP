package com.genisource.erp.inventory.warehouse;

/**
 * What a warehouse is mainly used for.
 *
 * <p>The type is advisory, not a hard constraint: nothing stops a semi-finished buffer
 * from sitting in a general warehouse. It exists so a factory can filter its own
 * screens — "where is my raw material?" — without maintaining a naming convention.
 */
public enum WarehouseType {

    /** Raw material: bought in, consumed by production. */
    RAW,
    /** Work in progress and semi-finished goods. */
    SEMI,
    /** Finished goods awaiting shipment. */
    FINISHED,
    /** Everything else: returns, spares, packaging. */
    GENERAL
}
