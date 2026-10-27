package com.genisource.erp.mrp;

/**
 * What happened to a plan row.
 *
 * <p>A plan is advice. Marking it converted records that somebody acted on it and which
 * document came out, so the next run can explain why yesterday's shortage is gone rather
 * than silently re-planning the same item.
 */
public enum MrpPlanStatus {

    /** Recommended and not yet acted on. */
    PLANNED,

    /** Turned into a purchase order or a works order. */
    CONVERTED,

    /** Deliberately dropped — the planner knows something the plan does not. */
    IGNORED;

    public boolean isOpen() {
        return this == PLANNED;
    }
}
