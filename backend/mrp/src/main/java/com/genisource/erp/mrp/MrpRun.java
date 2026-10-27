package com.genisource.erp.mrp;

import com.genisource.erp.platform.common.TenantAppendOnlyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * One calculation, and the parameters it was given.
 *
 * <p>Kept forever. Next month's planner is going to ask why three hundred of something
 * were bought in October, and a table that only holds the latest answer cannot tell them.
 * The totals are denormalised on purpose: a run list should not have to count its own
 * children to say how many lines it produced.
 *
 * <p>Append-only, so {@code created_at} is the run time. A separate {@code run_at} column
 * would hold the same instant.
 */
@Entity
@Table(name = "mrp_run")
public class MrpRun extends TenantAppendOnlyEntity {

    @Column(name = "run_no", nullable = false, length = 32)
    private String runNo;

    /**
     * Always {@code COMPLETED} today: a run that fails rolls back, so a half-written run
     * never exists. The column is here so an asynchronous runner can appear later without
     * a migration.
     */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "COMPLETED";

    @Column(name = "horizon_days", nullable = false)
    private int horizonDays = 30;

    @Column(name = "demand_from")
    private LocalDate demandFrom;

    @Column(name = "demand_to")
    private LocalDate demandTo;

    @Column(name = "include_safety", nullable = false)
    private boolean includeSafety = true;

    @Column(name = "max_levels", nullable = false)
    private int maxLevels = 12;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "supply_count", nullable = false)
    private int supplyCount;

    @Column(name = "shortage_count", nullable = false)
    private int shortageCount;

    @Column(name = "run_by", length = 64)
    private String runBy;

    @Column(name = "remark", length = 500)
    private String remark;

    protected MrpRun() {
    }

    public MrpRun(String runNo, int horizonDays, LocalDate demandFrom, LocalDate demandTo,
                  boolean includeSafety, int maxLevels, String runBy, String remark) {
        this.runNo = runNo;
        this.horizonDays = horizonDays;
        this.demandFrom = demandFrom;
        this.demandTo = demandTo;
        this.includeSafety = includeSafety;
        this.maxLevels = maxLevels;
        this.runBy = runBy;
        this.remark = remark;
    }

    /** Records what the run produced; written once, before the transaction commits. */
    public void recordTotals(int itemCount, int supplyCount, int shortageCount) {
        this.itemCount = itemCount;
        this.supplyCount = supplyCount;
        this.shortageCount = shortageCount;
    }

    public String getRunNo() {
        return runNo;
    }

    public String getStatus() {
        return status;
    }

    public int getHorizonDays() {
        return horizonDays;
    }

    public LocalDate getDemandFrom() {
        return demandFrom;
    }

    public LocalDate getDemandTo() {
        return demandTo;
    }

    public boolean isIncludeSafety() {
        return includeSafety;
    }

    public int getMaxLevels() {
        return maxLevels;
    }

    public int getItemCount() {
        return itemCount;
    }

    public int getSupplyCount() {
        return supplyCount;
    }

    public int getShortageCount() {
        return shortageCount;
    }

    public String getRunBy() {
        return runBy;
    }

    public String getRemark() {
        return remark;
    }
}
