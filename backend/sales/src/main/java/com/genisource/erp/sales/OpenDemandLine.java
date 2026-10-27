package com.genisource.erp.sales;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One open sales order line, as MRP sees it.
 *
 * <p>Only what planning needs: which item, how much, and by when. The price is
 * deliberately absent — a material plan that changed because somebody renegotiated a
 * unit price would be a plan nobody trusts.
 *
 * <p>The reservation this line holds is deliberately absent too. It is not a separate
 * claim: the units it protects are part of the stock figure the plan already nets
 * against, and the order it protects is the demand being netted. Deducting it as well
 * would count the same requirement twice.
 */
public record OpenDemandLine(Long itemId,
                             BigDecimal qty,
                             LocalDate requiredDate,
                             String docNo) {
}
