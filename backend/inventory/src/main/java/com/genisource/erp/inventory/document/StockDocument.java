package com.genisource.erp.inventory.document;

import com.genisource.erp.platform.common.VersionedTenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A stock document: one envelope for receipts, issues, transfers and counts.
 *
 * <p>The source and destination live on the header, not on the line, because a
 * transfer is a warehouse-to-warehouse move: every line travels together. Lines that
 * each picked their own source bin would turn one transfer into a hundred movements.
 *
 * <p>A document is created as a draft and only touches stock when it is posted. Once
 * posted it is frozen; the only way back is a reversal, which is a separate document.
 */
@Entity
@Table(name = "inv_document")
public class StockDocument extends VersionedTenantEntity {

    @Column(name = "doc_no", nullable = false, length = 32)
    private String docNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 16)
    private DocumentType docType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DocumentStatus status = DocumentStatus.DRAFT;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "to_warehouse_id")
    private Long toWarehouseId;

    @Column(name = "to_location_id")
    private Long toLocationId;

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "biz_date", nullable = false)
    private LocalDate bizDate;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "reversal_of")
    private Long reversalOf;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<DocumentLine> lines = new ArrayList<>();

    protected StockDocument() {
    }

    public StockDocument(String docNo, DocumentType docType, Long warehouseId, LocalDate bizDate) {
        this.docNo = docNo;
        this.docType = docType;
        this.warehouseId = warehouseId;
        this.bizDate = bizDate;
    }

    /** Replaces the lines wholesale; used when a draft is edited. */
    public void replaceLines(List<DocumentLine> replacement) {
        lines.clear();
        replacement.forEach(line -> {
            line.attachTo(this);
            lines.add(line);
        });
    }

    public void post(Instant at, String operator) {
        this.status = DocumentStatus.POSTED;
        this.postedAt = at;
        this.createdBy = operator;
    }

    public void markReversed() {
        this.status = DocumentStatus.REVERSED;
    }

    public String getDocNo() {
        return docNo;
    }

    public void setDocNo(String docNo) {
        this.docNo = docNo;
    }

    public DocumentType getDocType() {
        return docType;
    }

    public void setDocType(DocumentType docType) {
        this.docType = docType;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
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

    public Long getToWarehouseId() {
        return toWarehouseId;
    }

    public void setToWarehouseId(Long toWarehouseId) {
        this.toWarehouseId = toWarehouseId;
    }

    public Long getToLocationId() {
        return toLocationId;
    }

    public void setToLocationId(Long toLocationId) {
        this.toLocationId = toLocationId;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(Long partnerId) {
        this.partnerId = partnerId;
    }

    public LocalDate getBizDate() {
        return bizDate;
    }

    public void setBizDate(LocalDate bizDate) {
        this.bizDate = bizDate;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getReversalOf() {
        return reversalOf;
    }

    public void setReversalOf(Long reversalOf) {
        this.reversalOf = reversalOf;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public List<DocumentLine> getLines() {
        return lines;
    }
}
