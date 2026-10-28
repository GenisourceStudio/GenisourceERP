package com.genisource.erp.platform.production;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Announces that a works order was released and is ready for the shop floor.
 *
 * <p>Production must not depend on MES, yet releasing an order is exactly the moment
 * an MES would want to hear about. The interface lives here so the dependency points
 * one way — the integration module implements it, production announces to every
 * implementation it finds.
 *
 * <p>A build with no MES beside it has no listeners, and releasing an order is then
 * simply what it says: the order is released.
 */
public interface ProductionReleaseListener {

    /**
     * A works order was released.
     *
     * @param orderId    the works order
     * @param orderNo    its document number
     * @param itemCode   what is being made
     * @param itemName   what it is called
     * @param plannedQty how many
     * @param uom        the unit
     * @param plannedStart when the shop should start
     * @param plannedEnd   when it should be finished
     * @param bomVersion   the BOM revision frozen onto the order
     */
    void onReleased(Long orderId, String orderNo, String itemCode, String itemName,
                    BigDecimal plannedQty, String uom, LocalDate plannedStart,
                    LocalDate plannedEnd, String bomVersion);
}
