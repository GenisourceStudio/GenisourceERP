package com.genisource.erp.platform.production;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Announces that a works order moved value.
 *
 * <p>Production must not depend on finance, yet every issue and every completion is an
 * accounting event: material leaving the store is value moving into work in process,
 * and finished goods arriving is value moving out of it. The interface lives here so
 * the dependency points one way — finance implements it, production announces to every
 * implementation it finds.
 *
 * <p>The same holds for any other module that will one day care about production
 * (costing, APS). Nobody announces to a listener that is not deployed, which is the
 * correct behaviour for a build without accounting at all.
 */
public interface ProductionCostListener {

    /**
     * Material was issued to the line.
     *
     * @param orderId       the works order
     * @param orderNo       its document number, for the voucher's summary line
     * @param bizDate       the issue date
     * @param amountByItem  the value that left the store per item, at the cost the stock
     *                      ledger recorded — not at a standard, because this is what
     *                      inventory now says the material was worth. The breakdown
     *                      matters: raw material and sub-assemblies sit in different
     *                      accounts, and a listener that only got a total would have to
     *                      guess where the value came from
     * @param stockDocument the outbound stock document the issue posted — the anchor
     *                      that keeps one issue one voucher, however many balance
     *                      lines it drew from
     */
    void onMaterialIssued(Long orderId, String orderNo, LocalDate bizDate,
                          java.util.Map<Long, BigDecimal> amountByItem, Long stockDocument);

    /**
     * Goods were completed into stock.
     *
     * @param amount        the value that arrived, at the item's standard cost
     * @param stockDocument the inbound stock document this completion posted
     */
    void onCompletion(Long orderId, String orderNo, LocalDate bizDate, BigDecimal amount,
                      Long stockDocument);
}
