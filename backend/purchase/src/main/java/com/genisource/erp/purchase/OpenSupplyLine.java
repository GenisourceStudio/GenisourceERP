package com.genisource.erp.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Material already on order, as MRP sees it: still coming, so it can be netted against
 * demand instead of ordered a second time.
 *
 * <p>Only approved orders count. A draft purchase order is a suggestion, and netting a
 * suggestion would let a plan quieten a shortage that nobody has actually committed to
 * fixing.
 */
public record OpenSupplyLine(Long itemId,
                             BigDecimal qty,
                             LocalDate dueDate,
                             String docNo) {
}
