package com.genisource.erp.production;

import com.genisource.erp.platform.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One component the order will consume, with the loss rate already applied.
 *
 * <p>{@code qtyRequired} is what the shop floor is expected to use, not what the BOM says
 * per unit — a component with a five percent scrap allowance on a hundred-unit order
 * reads as a hundred and five, so what is printed on the picking list is what will
 * actually be picked.
 *
 * <p>Three quantities rather than one because they answer three different questions:
 * required is the plan, issued is what has physically moved, and locked is what is being
 * held for the day somebody walks over with a trolley. Required minus issued is the
 * shortage the order still faces; required minus issued minus locked is what release
 * could still commit.
 */
@Entity
@Table(name = "prd_order_line")
public class ProductionOrderLine extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private ProductionOrder order;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "qty_required", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyRequired;

    @Column(name = "qty_issued", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyIssued = BigDecimal.ZERO;

    @Column(name = "qty_locked", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyLocked = BigDecimal.ZERO;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "remark", length = 255)
    private String remark;

    protected ProductionOrderLine() {
    }

    public ProductionOrderLine(Integer lineNo, Long itemId, BigDecimal qtyRequired,
                               Long warehouseId, String remark) {
        this.lineNo = lineNo;
        this.itemId = itemId;
        this.qtyRequired = qtyRequired;
        this.warehouseId = warehouseId;
        this.remark = remark;
    }

    /** How much of this component has not been issued yet. */
    public BigDecimal openQty() {
        BigDecimal open = qtyRequired.subtract(qtyIssued);
        return open.signum() > 0 ? open : BigDecimal.ZERO;
    }

    /** How much release could still commit — what is neither issued nor already held. */
    public BigDecimal lockableQty() {
        BigDecimal lockable = openQty().subtract(qtyLocked);
        return lockable.signum() > 0 ? lockable : BigDecimal.ZERO;
    }

    public void lock(BigDecimal qty) {
        this.qtyLocked = this.qtyLocked.add(qty);
    }

    public void releaseLock(BigDecimal qty) {
        this.qtyLocked = this.qtyLocked.subtract(qty);
        if (this.qtyLocked.signum() < 0) {
            this.qtyLocked = BigDecimal.ZERO;
        }
    }

    public void issue(BigDecimal qty) {
        this.qtyIssued = this.qtyIssued.add(qty);
        if (this.qtyIssued.compareTo(this.qtyRequired) > 0) {
            this.qtyIssued = this.qtyRequired;
        }
    }

    void attachTo(ProductionOrder owner) {
        this.order = owner;
    }

    /**
     * Copies what the planner typed, leaving consumption state alone.
     *
     * <p>{@code qtyIssued} and {@code qtyLocked} are not content: they are what has
     * already happened to this line. An edit to a planned order that silently reset them
     * would let the same material be issued twice.
     */
    void copyContentFrom(ProductionOrderLine replacement) {
        this.itemId = replacement.itemId;
        this.qtyRequired = replacement.qtyRequired;
        this.warehouseId = replacement.warehouseId;
        this.remark = replacement.remark;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public Long getItemId() {
        return itemId;
    }

    public BigDecimal getQtyRequired() {
        return qtyRequired;
    }

    public BigDecimal getQtyIssued() {
        return qtyIssued;
    }

    public BigDecimal getQtyLocked() {
        return qtyLocked;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getRemark() {
        return remark;
    }
}
