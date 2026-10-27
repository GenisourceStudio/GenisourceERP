package com.genisource.erp.purchase;

import com.genisource.erp.platform.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One line of a purchase requisition, order or receipt.
 *
 * <p>The money on a line is written when the line is, not computed on the way out.
 * Net is what the accounts need, gross is what the supplier invoices, and storing both
 * means a rounding rule is applied exactly once — when somebody typed the price.
 *
 * <p>{@code sourceLineId} is the chain: an order line remembers the requisition line
 * that asked for it, and a receipt line the order line it satisfies. Without it,
 * "why did we buy two hundred of these" has no answer anywhere in the data.
 */
@Entity
@Table(name = "pur_document_line")
public class PurchaseDocumentLine extends TenantScopedEntity {

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private PurchaseDocument document;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal qty;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false, precision = 9, scale = 6)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "qty_fulfilled", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyFulfilled = BigDecimal.ZERO;

    @Column(name = "source_line_id")
    private Long sourceLineId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "remark", length = 255)
    private String remark;

    protected PurchaseDocumentLine() {
    }

    public PurchaseDocumentLine(Integer lineNo, Long itemId, BigDecimal qty,
                                BigDecimal unitPrice, BigDecimal taxRate, String remark) {
        this.lineNo = lineNo;
        this.itemId = itemId;
        this.qty = qty;
        this.unitPrice = unitPrice;
        this.taxRate = taxRate;
        this.remark = remark;
        recalculate();
    }

    /** Net, tax and gross from quantity, price and rate. */
    public void recalculate() {
        this.netAmount = qty.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);
        this.taxAmount = netAmount.multiply(taxRate).setScale(4, RoundingMode.HALF_UP);
        this.totalAmount = netAmount.add(taxAmount);
    }

    /** How much of this line the next document has not taken yet. */
    public BigDecimal openQty() {
        BigDecimal open = qty.subtract(qtyFulfilled);
        return open.signum() > 0 ? open : BigDecimal.ZERO;
    }

    public boolean isFulfilled() {
        return openQty().signum() == 0;
    }

    /** Records that the next document took {@code qty} more from this line. */
    public void fulfil(BigDecimal qty) {
        this.qtyFulfilled = this.qtyFulfilled.add(qty);
        if (this.qtyFulfilled.compareTo(this.qty) > 0) {
            this.qtyFulfilled = this.qty;
        }
    }

    /** Gives quantity back, because the document that took it was reversed. */
    public void unfulfil(BigDecimal qty) {
        this.qtyFulfilled = this.qtyFulfilled.subtract(qty);
        if (this.qtyFulfilled.signum() < 0) {
            this.qtyFulfilled = BigDecimal.ZERO;
        }
    }

    void attachTo(PurchaseDocument owner) {
        this.document = owner;
    }

    /**
     * Copies what the user typed onto this row, leaving the chain state alone.
     *
     * <p>{@code sourceLineId} and {@code qtyFulfilled} are not content — they are what
     * links this line to the requisition that asked for it and to the receipt that took
     * it. Overwriting them while editing a draft would silently unlink the chain.
     */
    void copyContentFrom(PurchaseDocumentLine replacement) {
        this.itemId = replacement.itemId;
        this.qty = replacement.qty;
        this.unitPrice = replacement.unitPrice;
        this.taxRate = replacement.taxRate;
        this.remark = replacement.remark;
        this.warehouseId = replacement.warehouseId;
        this.locationId = replacement.locationId;
        this.lotId = replacement.lotId;
        recalculate();
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getQtyFulfilled() {
        return qtyFulfilled;
    }

    public Long getSourceLineId() {
        return sourceLineId;
    }

    public void setSourceLineId(Long sourceLineId) {
        this.sourceLineId = sourceLineId;
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

    public Long getLotId() {
        return lotId;
    }

    public void setLotId(Long lotId) {
        this.lotId = lotId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
