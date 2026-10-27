package com.genisource.erp.production;

/**
 * Whether an operation on a works order has been carried out.
 *
 * <p>M4 keeps operations as the agreed work scope — they are copied from the routing when
 * the order is raised so the shop floor and the planner are discussing the same steps.
 * Executing them step by step, with reported times, belongs to the MES; until that
 * integration exists (M5) they are marked done in bulk when the order completes.
 */
public enum OperationStatus {

    PENDING,
    DONE;

    public boolean isDone() {
        return this == DONE;
    }
}
