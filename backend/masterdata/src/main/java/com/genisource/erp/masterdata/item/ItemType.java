package com.genisource.erp.masterdata.item;

/**
 * Engineering nature of an item.
 *
 * <p>Drives which facets of the item form make sense: a service has no stock and no BOM,
 * a finished good usually has both, a raw material is bought and never sold.
 */
public enum ItemType {

    /** 原材料 — bought and consumed, never sold. */
    RAW,

    /** 半成品 — made in-house and also a component of something else. */
    SEMI,

    /** 成品 — end product sold to customers. */
    FINISHED,

    /** 辅料 — consumables such as glue, gloves, packaging. */
    CONSUMABLE,

    /** 服务 — non-physical, e.g. outsourced processing or freight. */
    SERVICE;

    /** Services cannot be manufactured, stocked, or lot controlled. */
    public boolean isPhysical() {
        return this != SERVICE;
    }
}
