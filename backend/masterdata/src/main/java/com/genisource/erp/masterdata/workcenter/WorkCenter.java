package com.genisource.erp.masterdata.workcenter;

import com.genisource.erp.masterdata.shared.RecordStatus;
import com.genisource.erp.platform.common.VersionedTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A production resource that operations are scheduled on: a machine, a cell, an
 * assembly line, or a whole workshop.
 *
 * <p>Capacity and cost both live here so that a routing can be costed and load-checked
 * without a separate capacity model. M4 uses {@link #availableMinutesPerDay()} for
 * rough-cut capacity; M5 turns {@link #getCostRatePerHour()} into actual labour cost.
 */
@Entity
@Table(name = "md_work_center")
public class WorkCenter extends VersionedTenantEntity {

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "department", length = 64)
    private String department;

    /** Gross available time per day, before efficiency. Two shifts → 960. */
    @Column(name = "capacity_minutes_per_day", nullable = false)
    private int capacityMinutesPerDay = 480;

    /** Percentage; 85 means the resource delivers 85 % of its theoretical minutes. */
    @Column(name = "efficiency_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal efficiencyPercent = new BigDecimal("100");

    /** Fully loaded cost of one hour of this resource, in the tenant currency. */
    @Column(name = "cost_rate_per_hour", nullable = false, precision = 18, scale = 6)
    private BigDecimal costRatePerHour = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;

    protected WorkCenter() {
    }

    public WorkCenter(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /** Capacity actually usable per day, i.e. gross minutes scaled by efficiency. */
    public int availableMinutesPerDay() {
        BigDecimal effective = BigDecimal.valueOf(capacityMinutesPerDay)
                .multiply(effectiveEfficiencyPercent())
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return effective.intValue();
    }

    private BigDecimal effectiveEfficiencyPercent() {
        return efficiencyPercent == null ? new BigDecimal("100") : efficiencyPercent;
    }

    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }

    public void tidy() {
        this.department = department == null || department.isBlank() ? null : department.trim();
        this.efficiencyPercent = efficiencyPercent == null ? new BigDecimal("100") : efficiencyPercent;
        this.costRatePerHour = costRatePerHour == null ? BigDecimal.ZERO : costRatePerHour;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public int getCapacityMinutesPerDay() {
        return capacityMinutesPerDay;
    }

    public void setCapacityMinutesPerDay(int capacityMinutesPerDay) {
        this.capacityMinutesPerDay = capacityMinutesPerDay;
    }

    public BigDecimal getEfficiencyPercent() {
        return efficiencyPercent;
    }

    public void setEfficiencyPercent(BigDecimal efficiencyPercent) {
        this.efficiencyPercent = efficiencyPercent;
    }

    public BigDecimal getCostRatePerHour() {
        return costRatePerHour;
    }

    public void setCostRatePerHour(BigDecimal costRatePerHour) {
        this.costRatePerHour = costRatePerHour;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public void setStatus(RecordStatus status) {
        this.status = status;
    }
}
