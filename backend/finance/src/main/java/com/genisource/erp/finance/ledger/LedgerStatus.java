package com.genisource.erp.finance.ledger;

/**
 * Where a receivable or a payable stands.
 *
 * <p>Derived from the amounts, never typed in by anybody. A status that had to be
 * maintained by hand would drift away from the money the moment somebody forgot to
 * update it, and then "how much is still open" would depend on which field you trust.
 */
public enum LedgerStatus {

    /** Nothing has been settled yet. */
    OPEN,
    /** Some has been settled, but not all of it. */
    PARTIAL,
    /** Settled in full. */
    SETTLED,
    /** The document that created it was reversed; the amount no longer counts. */
    VOID;

    /**
     * The status implied by the amounts.
     *
     * @param total what was owed
     * @param paid  what has been settled against it
     */
    public static LedgerStatus of(java.math.BigDecimal total, java.math.BigDecimal paid) {
        if (paid.signum() <= 0) {
            return OPEN;
        }
        return paid.compareTo(total) >= 0 ? SETTLED : PARTIAL;
    }

    /** Whether the row still counts as money owed. */
    public boolean isOpen() {
        return this == OPEN || this == PARTIAL;
    }
}
