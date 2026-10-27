package com.genisource.erp.masterdata.routing;

import com.genisource.erp.platform.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One operation of a routing: cutting, welding, painting, final inspection.
 *
 * <p>Times are split into setup (once per batch) and run (per unit) because that is how
 * capacity and cost are actually calculated: 30 minutes to change a die does not scale
 * with order quantity, while 2 minutes of welding does.
 */
@Entity
@Table(name = "md_routing_op")
public class RoutingOperation extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "routing_id", nullable = false)
    private Routing routing;

    /** Step number; ordered ascending. 10, 20, 30 leaves room to insert steps later. */
    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "work_center_id", nullable = false)
    private Long workCenterId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "setup_minutes", nullable = false, precision = 12, scale = 4)
    private BigDecimal setupMinutes = BigDecimal.ZERO;

    @Column(name = "run_minutes_per_unit", nullable = false, precision = 12, scale = 4)
    private BigDecimal runMinutesPerUnit = BigDecimal.ZERO;

    @Column(name = "remark", length = 255)
    private String remark;

    protected RoutingOperation() {
    }

    public RoutingOperation(int seq, Long workCenterId, String name) {
        this.seq = seq;
        this.workCenterId = workCenterId;
        this.name = name;
    }

    void attachTo(Routing routing) {
        this.routing = routing;
    }

    public void tidy() {
        this.remark = remark == null || remark.isBlank() ? null : remark.trim();
        this.setupMinutes = setupMinutes == null ? BigDecimal.ZERO : setupMinutes;
        this.runMinutesPerUnit = runMinutesPerUnit == null ? BigDecimal.ZERO : runMinutesPerUnit;
    }

    public Routing getRouting() {
        return routing;
    }

    public int getSeq() {
        return seq;
    }

    public Long getWorkCenterId() {
        return workCenterId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getSetupMinutes() {
        return setupMinutes;
    }

    public void setSetupMinutes(BigDecimal setupMinutes) {
        this.setupMinutes = setupMinutes;
    }

    public BigDecimal getRunMinutesPerUnit() {
        return runMinutesPerUnit;
    }

    public void setRunMinutesPerUnit(BigDecimal runMinutesPerUnit) {
        this.runMinutesPerUnit = runMinutesPerUnit;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
