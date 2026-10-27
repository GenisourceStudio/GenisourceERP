package com.genisource.erp.inventory.movement;

/**
 * The kind of change a movement records.
 *
 * <p>Direction says which way the quantity moved; the type says why. Both are needed:
 * a reader scanning the ledger wants to see "this was a transfer" without having to
 * compare warehouse ids, and a reader filtering by direction wants "everything that
 * left" without knowing the reason.
 */
public enum MovementType {

    /** Goods received into a warehouse. */
    RECEIPT(true),
    /** Goods issued out of a warehouse. */
    ISSUE(false),
    /** Goods leaving one warehouse as part of a transfer. */
    TRANSFER_OUT(false),
    /** Goods arriving in another warehouse as part of a transfer. */
    TRANSFER_IN(true),
    /** A physical count found more than the book said. */
    COUNT_GAIN(true),
    /** A physical count found less than the book said. */
    COUNT_LOSS(false);

    private final boolean inbound;

    MovementType(boolean inbound) {
        this.inbound = inbound;
    }

    /** {@code true} when the movement increases the balance. */
    public boolean isInbound() {
        return inbound;
    }

    /**
     * The movement that undoes this one.
     *
     * <p>A reversal writes the opposite kind of entry next to the original: what came
     * in goes out, and what left comes back. The value travels with it, so the
     * reversal restores the exact amount the original carried.
     */
    public MovementType opposite() {
        return switch (this) {
            case RECEIPT -> ISSUE;
            case ISSUE -> RECEIPT;
            case TRANSFER_OUT -> TRANSFER_IN;
            case TRANSFER_IN -> TRANSFER_OUT;
            case COUNT_GAIN -> COUNT_LOSS;
            case COUNT_LOSS -> COUNT_GAIN;
        };
    }
}
