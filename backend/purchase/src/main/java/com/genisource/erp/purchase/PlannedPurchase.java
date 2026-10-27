package com.genisource.erp.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line of a material plan, handed to purchasing to become a real order.
 *
 * <p>Deliberately narrow. A planner has decided the item, the quantity and the date; the
 * buyer still owns the supplier, the price and the terms, and a plan that filled those in
 * would be planning with last month's prices.
 *
 * @param supplierId the supplier, normally the item's default one
 * @param unitPrice  price excluding tax; null leaves the line at zero for the buyer to fill
 * @param sourceId   the plan row this came from, so the order can be traced back to a run
 */
public record PlannedPurchase(Long supplierId,
                              Long itemId,
                              BigDecimal qty,
                              BigDecimal unitPrice,
                              BigDecimal taxRate,
                              LocalDate bizDate,
                              LocalDate requiredDate,
                              String sourceId,
                              String remark) {
}
