package com.genisource.erp.production;

import com.genisource.erp.platform.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One step of the route, frozen onto the order.
 *
 * <p>Copied from the routing rather than referenced, for the same reason the components
 * are: a route revised mid-order must not rewrite work that has already been briefed. The
 * times are the routing's estimates, scaled by the order quantity at the point of copying.
 */
@Entity
@Table(name = "prd_order_operation")
public class ProductionOrderOperation extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private ProductionOrder order;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "work_center_id")
    private Long workCenterId;

    @Column(name = "setup_minutes", nullable = false, precision = 9, scale = 2)
    private BigDecimal setupMinutes = BigDecimal.ZERO;

    @Column(name = "run_minutes", nullable = false, precision = 9, scale = 2)
    private BigDecimal runMinutes = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OperationStatus status = OperationStatus.PENDING;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ProductionOrderOperation() {
    }

    public ProductionOrderOperation(Integer sequence, String name, Long workCenterId,
                                    BigDecimal setupMinutes, BigDecimal runMinutes) {
        this.sequence = sequence;
        this.name = name;
        this.workCenterId = workCenterId;
        this.setupMinutes = setupMinutes == null ? BigDecimal.ZERO : setupMinutes;
        this.runMinutes = runMinutes == null ? BigDecimal.ZERO : runMinutes;
    }

    public void markDone() {
        this.status = OperationStatus.DONE;
        this.completedAt = Instant.now();
    }

    void attachTo(ProductionOrder owner) {
        this.order = owner;
    }

    /**
     * The order this step belongs to.
     *
     * <p>Exposed so callers can group operations by order without a second query. It is a
     * lazy proxy, and only its id is read, which Hibernate answers without hitting the
     * database.
     */
    public ProductionOrder getOrder() {
        return order;
    }

    public Integer getSequence() {
        return sequence;
    }

    public String getName() {
        return name;
    }

    public Long getWorkCenterId() {
        return workCenterId;
    }

    public BigDecimal getSetupMinutes() {
        return setupMinutes;
    }

    public BigDecimal getRunMinutes() {
        return runMinutes;
    }

    public OperationStatus getStatus() {
        return status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
