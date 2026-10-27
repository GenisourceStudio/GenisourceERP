package com.genisource.erp.purchase;

import com.genisource.erp.platform.common.DocumentLifecycle;
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
 * A purchase requisition, order or goods receipt.
 *
 * <p>The header carries the money as three numbers — net, tax, gross — because the
 * supplier invoices the gross and the accounts need the net, and deriving one from the
 * other at read time would mean every screen agreed on a rounding rule. They are
 * written together when a line changes.
 *
 * <p>Approval leaves a trace rather than a status of its own. A document that has been
 * approved and is half delivered is both, and two fields would have to be kept in step
 * by hand.
 */
@Entity
@Table(name = "pur_document")
public class PurchaseDocument extends VersionedTenantEntity {

    @Column(name = "doc_no", nullable = false, length = 32)
    private String docNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 16)
    private PurchaseDocType docType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DocumentLifecycle status = DocumentLifecycle.DRAFT;

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "biz_date", nullable = false)
    private LocalDate bizDate;

    @Column(name = "required_date")
    private LocalDate requiredDate;

    @Column(name = "payment_term_days", nullable = false)
    private int paymentTermDays;

    @Column(name = "net_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Column(name = "source_id", length = 64)
    private String sourceId;

    @Column(name = "submitted_by", length = 64)
    private String submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approval_remark", length = 255)
    private String approvalRemark;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "reversal_of")
    private Long reversalOf;

    @Column(name = "remark", length = 500)
    private String remark;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<PurchaseDocumentLine> lines = new ArrayList<>();

    protected PurchaseDocument() {
    }

    public PurchaseDocument(String docNo,
                            PurchaseDocType docType,
                            Long warehouseId,
                            LocalDate bizDate) {
        this.docNo = docNo;
        this.docType = docType;
        this.warehouseId = warehouseId;
        this.bizDate = bizDate;
    }

    /**
     * Replaces the lines wholesale; used when a draft is edited.
     *
     * <p>Lines are matched by line number and updated in place instead of being deleted
     * and re-inserted. Two reasons, and the second is the one that bites: Hibernate
     * writes inserts before deletes, so a delete-then-insert of line 10 would collide
     * with {@code uq_pur_document_line_no}; and a re-inserted line gets a new id, which
     * strands the receipt lines that point at the old one through
     * {@code source_line_id}. Keeping the row keeps the id.
     */
    public void replaceLines(List<PurchaseDocumentLine> replacement) {
        Map<Integer, PurchaseDocumentLine> incoming = new LinkedHashMap<>();
        for (PurchaseDocumentLine line : replacement) {
            line.attachTo(this);
            incoming.put(line.getLineNo(), line);
        }
        for (PurchaseDocumentLine existing : new ArrayList<>(lines)) {
            PurchaseDocumentLine next = incoming.remove(existing.getLineNo());
            if (next == null) {
                lines.remove(existing);
            } else {
                existing.copyContentFrom(next);
            }
        }
        lines.addAll(incoming.values());
    }

    /** Recomputes the header money from the lines; the only place the totals are set. */
    public void recalculate() {
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseDocumentLine line : lines) {
            net = net.add(line.getNetAmount());
            tax = tax.add(line.getTaxAmount());
            total = total.add(line.getTotalAmount());
        }
        this.netAmount = net;
        this.taxAmount = tax;
        this.totalAmount = total;
    }

    public void submit(String operator) {
        this.status = DocumentLifecycle.SUBMITTED;
        this.submittedBy = operator;
        this.submittedAt = Instant.now();
    }

    public void approve(String operator, String note) {
        this.status = DocumentLifecycle.APPROVED;
        this.approvedBy = operator;
        this.approvedAt = Instant.now();
        this.approvalRemark = note;
    }

    public void reject(String operator, String note) {
        this.status = DocumentLifecycle.REJECTED;
        this.approvedBy = operator;
        this.approvedAt = Instant.now();
        this.approvalRemark = note;
    }

    public void post(String operator) {
        this.status = DocumentLifecycle.POSTED;
        this.postedAt = Instant.now();
        this.submittedBy = submittedBy == null ? operator : submittedBy;
    }

    public void markReversed() {
        this.status = DocumentLifecycle.REVERSED;
    }

    /** Whether every line has had its full quantity taken by the next document. */
    public boolean isFulfilled() {
        return lines.stream().allMatch(PurchaseDocumentLine::isFulfilled);
    }

    /** Whether any line has had any quantity taken. */
    public boolean isPartlyFulfilled() {
        return lines.stream().anyMatch(line -> line.getQtyFulfilled().signum() > 0);
    }

    public String getDocNo() {
        return docNo;
    }

    public PurchaseDocType getDocType() {
        return docType;
    }

    public DocumentLifecycle getStatus() {
        return status;
    }

    public void setStatus(DocumentLifecycle status) {
        this.status = status;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(Long partnerId) {
        this.partnerId = partnerId;
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

    public LocalDate getBizDate() {
        return bizDate;
    }

    public void setBizDate(LocalDate bizDate) {
        this.bizDate = bizDate;
    }

    public LocalDate getRequiredDate() {
        return requiredDate;
    }

    public void setRequiredDate(LocalDate requiredDate) {
        this.requiredDate = requiredDate;
    }

    public int getPaymentTermDays() {
        return paymentTermDays;
    }

    public void setPaymentTermDays(int paymentTermDays) {
        this.paymentTermDays = paymentTermDays;
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

    public String getSubmittedBy() {
        return submittedBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public String getApprovalRemark() {
        return approvalRemark;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public Long getReversalOf() {
        return reversalOf;
    }

    public void setReversalOf(Long reversalOf) {
        this.reversalOf = reversalOf;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<PurchaseDocumentLine> getLines() {
        return lines;
    }
}
