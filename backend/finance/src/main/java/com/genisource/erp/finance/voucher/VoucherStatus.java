package com.genisource.erp.finance.voucher;

/**
 * A voucher's life, in three states.
 *
 * <p>DRAFT means the sentence is being written; nothing has happened to the books.
 * POSTED means it counts; every report reads only this state. REVERSED means a red-letter
 * voucher has cancelled it — the original stays on the books as evidence that it once
 * counted, and stops being summed.
 *
 * <p>The reversal is itself a POSTED voucher with the sides swapped, so the arithmetic
 * rule stays singular: <em>sum POSTED vouchers, nothing else</em>. An alternative that
 * kept the original counted and subtracted with negatives would need every query to
 * remember why two rows can describe one event.
 */
public enum VoucherStatus {
    DRAFT,
    POSTED,
    REVERSED;

    public boolean isPosted() {
        return this == POSTED;
    }

    /**
     * Whether this voucher counts towards the books.
     *
     * <p>Everything except a draft. A reversed voucher counts, and so does the
     * red-letter voucher that reverses it: the two cancel, which is how red-letter
     * reversal has always worked — the original stays in the book as evidence that it
     * once counted, and the correction stands beside it.
     *
     * <p>Excluding the original and keeping only the correction would leave the ledger
     * showing the <em>opposite</em> of what happened, because the correction's sides
     * are swapped.
     */
    public boolean isCounted() {
        return this != DRAFT;
    }

    /** The statuses a report sums over. */
    public static java.util.List<VoucherStatus> counted() {
        return java.util.List.of(POSTED, REVERSED);
    }

    public boolean isEditable() {
        return this == DRAFT;
    }
}
