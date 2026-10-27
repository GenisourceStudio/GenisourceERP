package com.genisource.erp.production;

import com.genisource.erp.platform.common.VersionedTenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A works order: make this many of this item, out of these components, through these steps.
 *
 * <p>The bill of materials and the routing are copied in when the order is raised and are
 * not read again afterwards. A BOM revised while the order is open must not change what
 * the shop floor is building — that is how a finished good ends up made from a structure
 * nobody agreed to, and the discrepancy only surfaces at costing time.
 *
 * <p>Raising the order does not commit any material; {@link #release} does. The gap
 * matters: a planner generates dozens of suggested orders, and committing stock for all
 * of them would starve the orders that are actually going to run.
 */
@Entity
@Table(name = "prd_production_order")
public class ProductionOrder extends VersionedTenantEntity {

    @Column(name = "doc_no", nullable = false, length = 32)
    private String docNo;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "bom_id")
    private Long bomId;

    @Column(name = "bom_version", length = 16)
    private String bomVersion;

    @Column(name = "qty_planned", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyPlanned;

    @Column(name = "qty_completed", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyCompleted = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ProductionOrderStatus status = ProductionOrderStatus.PLANNED;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "planned_start")
    private LocalDate plannedStart;

    @Column(name = "planned_end")
    private LocalDate plannedEnd;

    @Column(name = "actual_start")
    private Instant actualStart;

    @Column(name = "actual_end")
    private Instant actualEnd;

    @Column(name = "mes_wo_no", length = 64)
    private String mesWoNo;

    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Column(name = "source_id", length = 64)
    private String sourceId;

    @Column(name = "remark", length = 500)
    private String remark;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<ProductionOrderLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<ProductionOrderOperation> operations = new ArrayList<>();

    protected ProductionOrder() {
    }

    public ProductionOrder(String docNo, Long itemId, BigDecimal qtyPlanned) {
        this.docNo = docNo;
        this.itemId = itemId;
        this.qtyPlanned = qtyPlanned;
    }

    /**
     * Replaces the component list, matching by line number.
     *
     * <p>Same trap as everywhere else in this codebase: clearing and re-adding makes
     * Hibernate insert before it deletes, and the unique index on {@code (order_id,
     * line_no)} rejects the insert. Matching in place also keeps the row ids that an
     * earlier issue may still be pointing at.
     */
    public void replaceLines(List<ProductionOrderLine> replacement) {
        Map<Integer, ProductionOrderLine> incoming = new LinkedHashMap<>();
        for (ProductionOrderLine line : replacement) {
            line.attachTo(this);
            incoming.put(line.getLineNo(), line);
        }
        for (ProductionOrderLine existing : new ArrayList<>(lines)) {
            ProductionOrderLine next = incoming.remove(existing.getLineNo());
            if (next == null) {
                lines.remove(existing);
            } else {
                existing.copyContentFrom(next);
            }
        }
        lines.addAll(incoming.values());
    }

    /** Replaces the operation list; steps carry no accumulated state, so a plain swap is safe. */
    public void replaceOperations(List<ProductionOrderOperation> replacement) {
        operations.clear();
        for (ProductionOrderOperation operation : replacement) {
            operation.attachTo(this);
            operations.add(operation);
        }
    }

    /** Hands the order to the floor. Materials are committed by the service, not here. */
    public void release() {
        this.status = ProductionOrderStatus.RELEASED;
    }

    /** Records that work started; called on the first issue or completion. */
    public void markStarted() {
        if (this.actualStart == null) {
            this.actualStart = Instant.now();
        }
        if (this.status == ProductionOrderStatus.RELEASED) {
            this.status = ProductionOrderStatus.IN_PROGRESS;
        }
    }

    /** Books finished goods against the order and closes it out when the quantity is met. */
    public void registerCompletion(BigDecimal qty) {
        this.qtyCompleted = this.qtyCompleted.add(qty);
        if (this.qtyCompleted.compareTo(this.qtyPlanned) >= 0) {
            this.qtyCompleted = this.qtyPlanned;
            this.status = ProductionOrderStatus.COMPLETED;
            this.actualEnd = Instant.now();
        } else if (this.status == ProductionOrderStatus.RELEASED) {
            this.status = ProductionOrderStatus.IN_PROGRESS;
        }
    }

    /** How much of the planned quantity is still to be made. */
    public BigDecimal remainingQty() {
        BigDecimal remaining = qtyPlanned.subtract(qtyCompleted);
        return remaining.signum() > 0 ? remaining : BigDecimal.ZERO;
    }

    /** Wraps up an order that will not be finished. */
    public void close() {
        this.status = ProductionOrderStatus.CLOSED;
        this.actualEnd = Instant.now();
    }

    public void cancel() {
        this.status = ProductionOrderStatus.CANCELLED;
    }

    public String getDocNo() {
        return docNo;
    }

    public Long getItemId() {
        return itemId;
    }

    public Long getBomId() {
        return bomId;
    }

    public void setBomId(Long bomId) {
        this.bomId = bomId;
    }

    public String getBomVersion() {
        return bomVersion;
    }

    public void setBomVersion(String bomVersion) {
        this.bomVersion = bomVersion;
    }

    public BigDecimal getQtyPlanned() {
        return qtyPlanned;
    }

    public void setQtyPlanned(BigDecimal qtyPlanned) {
        this.qtyPlanned = qtyPlanned;
    }

    public BigDecimal getQtyCompleted() {
        return qtyCompleted;
    }

    public ProductionOrderStatus getStatus() {
        return status;
    }

    public void setStatus(ProductionOrderStatus status) {
        this.status = status;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public LocalDate getPlannedStart() {
        return plannedStart;
    }

    public void setPlannedStart(LocalDate plannedStart) {
        this.plannedStart = plannedStart;
    }

    public LocalDate getPlannedEnd() {
        return plannedEnd;
    }

    public void setPlannedEnd(LocalDate plannedEnd) {
        this.plannedEnd = plannedEnd;
    }

    public Instant getActualStart() {
        return actualStart;
    }

    public Instant getActualEnd() {
        return actualEnd;
    }

    public String getMesWoNo() {
        return mesWoNo;
    }

    public void setMesWoNo(String mesWoNo) {
        this.mesWoNo = mesWoNo;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<ProductionOrderLine> getLines() {
        return lines;
    }

    public List<ProductionOrderOperation> getOperations() {
        return operations;
    }
}
