package com.genisource.erp.inventory.movement;

import com.genisource.erp.platform.common.TenantAppendOnlyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One line of the inventory ledger.
 *
 * <p>Append-only. A movement is written when a document is posted and is never touched
 * again — not even by a reversal, which writes a new movement pointing the other way.
 * That is what makes the ledger trustworthy: nobody can quietly change history, and
 * the running balance is reproduced by adding up the movements.
 */
@Entity
@Table(name = "inv_movement")
public class Movement extends TenantAppendOnlyEntity {

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "document_line_id")
    private Long documentLineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 16)
    private MovementType movementType;

    @Column(name = "direction", nullable = false, length = 8)
    private String direction;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal qty;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "balance_qty_after", nullable = false, precision = 18, scale = 6)
    private BigDecimal balanceQtyAfter = BigDecimal.ZERO;

    @Column(name = "balance_amount_after", nullable = false, precision = 18, scale = 4)
    private BigDecimal balanceAmountAfter = BigDecimal.ZERO;

    @Column(name = "ref_type", length = 32)
    private String refType;

    @Column(name = "ref_id", length = 64)
    private String refId;

    @Column(name = "operator", length = 64)
    private String operator;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "moved_at", nullable = false)
    private Instant movedAt = Instant.now();

    public Movement() {
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getDocumentLineId() {
        return documentLineId;
    }

    public void setDocumentLineId(Long documentLineId) {
        this.documentLineId = documentLineId;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getLotId() {
        return lotId;
    }

    public void setLotId(Long lotId) {
        this.lotId = lotId;
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

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBalanceQtyAfter() {
        return balanceQtyAfter;
    }

    public void setBalanceQtyAfter(BigDecimal balanceQtyAfter) {
        this.balanceQtyAfter = balanceQtyAfter;
    }

    public BigDecimal getBalanceAmountAfter() {
        return balanceAmountAfter;
    }

    public void setBalanceAmountAfter(BigDecimal balanceAmountAfter) {
        this.balanceAmountAfter = balanceAmountAfter;
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public String getRefId() {
        return refId;
    }

    public void setRefId(String refId) {
        this.refId = refId;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Instant getMovedAt() {
        return movedAt;
    }

    public void setMovedAt(Instant movedAt) {
        this.movedAt = movedAt;
    }
}
