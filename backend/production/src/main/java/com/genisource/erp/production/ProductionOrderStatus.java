package com.genisource.erp.production;

/**
 * Where a works order has got to.
 *
 * <p>Its own enum rather than the shared {@code DocumentLifecycle}, because the steps are
 * different: a works order is not submitted for approval and cannot be rejected, it is
 * planned, released to the floor, worked on, finished and wrapped up. Forcing it into the
 * trade vocabulary would mean every screen explaining that "approved" here means
 * "released", which is the sort of translation layer that eventually gets it wrong.
 */
public enum ProductionOrderStatus {

    /** Raised, not yet handed to the shop floor. Its materials are not committed. */
    PLANNED,
    /** Released: materials are committed to it and the floor may start. */
    RELEASED,
    /** Some material has been issued or some quantity completed. */
    IN_PROGRESS,
    /** The planned quantity has been completed. */
    COMPLETED,
    /** Wrapped up short. Terminal; the balance will not be made on this order. */
    CLOSED,
    /** Abandoned before any material was consumed. */
    CANCELLED;

    /** Whether the order can still consume material or produce goods. */
    public boolean isOpen() {
        return this == PLANNED || this == RELEASED || this == IN_PROGRESS;
    }

    /** Whether it has reached an end and cannot move again. */
    public boolean isTerminal() {
        return this == COMPLETED || this == CLOSED || this == CANCELLED;
    }

    /** Whether the header and its lines may still be edited. */
    public boolean isEditable() {
        return this == PLANNED;
    }

    /** Whether material may be issued against it. */
    public boolean acceptsIssues() {
        return this == RELEASED || this == IN_PROGRESS;
    }

    /** Whether more finished goods may be booked in. */
    public boolean acceptsCompletions() {
        return this == RELEASED || this == IN_PROGRESS;
    }
}
