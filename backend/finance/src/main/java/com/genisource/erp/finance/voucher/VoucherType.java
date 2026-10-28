package com.genisource.erp.finance.voucher;

/**
 * What caused a voucher.
 *
 * <p>The type is how a reader answers "why does this voucher exist" without opening the
 * document behind it, and how a report separates trades the business chose (MANUAL,
 * OPENING) from ones its documents caused. It is not a state: the lifecycle lives in
 * {@link VoucherStatus}.
 */
public enum VoucherType {
    /** Typed by a person, from a paper slip or a month-end adjustment. */
    MANUAL,
    /** Opening balances, entered once when the ledger goes live. */
    OPENING,
    /** A posted goods receipt: stock in, payable up. */
    PURCHASE,
    /** A posted shipment: receivable up, revenue recognised. */
    SALES,
    /** Money paid to a supplier. */
    PAYMENT,
    /** Money collected from a customer. */
    RECEIPT,
    /** A works-order completion: finished goods in, work in process out. */
    PRODUCTION,
    /** The red-letter voucher that cancels another one. */
    REVERSAL
}
