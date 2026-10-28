package com.genisource.erp.finance.account;

/**
 * The jobs an account can be asked to do by the automatic posting rules.
 *
 * <p>A role is how code refers to an account without naming one. Posting rules say
 * "credit {@link #AP}" and the chart decides which account that is; renumbering the
 * chart, or splitting raw material out of a combined inventory account, becomes a
 * data change rather than a code change.
 *
 * <p>Only the roles an automatic rule actually uses are listed. Inventing roles for
 * accounts nothing posts to would give an administrator switches that do nothing.
 */
public enum AccountRole {
    /** Cash on hand. */
    CASH,
    /** Bank deposits: where payments and receipts actually move. */
    BANK,
    /** Trade receivables raised by a shipment. */
    AR,
    /** Trade payables raised by a goods receipt. */
    AP,
    /** Raw material and bought-in components, at moving average cost. */
    INVENTORY_RAW,
    /** Finished goods and sub-assemblies, at standard cost on completion. */
    INVENTORY_FINISHED,
    /** Work in process: the credit side of a completion. */
    WIP,
    /** Deductible input VAT on a purchase. */
    TAX_INPUT,
    /** Output VAT on a sale. */
    TAX_OUTPUT,
    /** Revenue recognised on a shipment. */
    REVENUE,
    /** Cost of the goods that were shipped. */
    COST_OF_SALES,
    /** Cumulative profit for the current year, before it is distributed. */
    PROFIT_CURRENT_YEAR
}
