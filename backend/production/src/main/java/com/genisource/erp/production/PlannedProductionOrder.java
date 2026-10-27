package com.genisource.erp.production;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A material plan row that recommends making something.
 *
 * <p>Carries the plan row's identity so the works order can name its origin, which is
 * what makes "why are we building three hundred of these" answerable three months later.
 *
 * @param dueDate  when the goods are wanted; M4 puts both the planned start and end there
 *                 because finite-capacity scheduling is not part of this milestone
 * @param sourceId the plan row this came from
 */
public record PlannedProductionOrder(Long itemId,
                                     BigDecimal qty,
                                     LocalDate dueDate,
                                     Long warehouseId,
                                     String sourceId,
                                     String remark) {
}
