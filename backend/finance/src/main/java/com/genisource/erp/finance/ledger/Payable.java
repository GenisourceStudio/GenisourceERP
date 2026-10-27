package com.genisource.erp.finance.ledger;

import com.genisource.erp.platform.common.VersionedTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Money the organisation owes a supplier.
 *
 * <p>Nobody types a payable in. It appears when a purchase receipt is posted, because
 * that is the moment the goods became ours and the debt became real. It remembers the
 * receipt that created it, which is why the pair is unique: posting the same receipt
 * twice — a retry, a double click — cannot create two debts.
 *
 * <p>M5 turns these rows into vouchers and posts them to the general ledger. Until
 * then they are the answer to "how much do we owe, to whom, and when is it due".
 */
@Entity
@Table(name = "fin_payable")
public class Payable extends VersionedTenantEntity implements LedgerRow {

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "source_no", nullable = false, length = 32)
    private String sourceNo;

    @Column(name = "biz_date", nullable = false)
    private LocalDate bizDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "net_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 18, scale = 4)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LedgerStatus status = LedgerStatus.OPEN;

    @Column(name = "remark", length = 500)
    private String remark;

    protected Payable() {
    }

    public Payable(Long partnerId,
                   String sourceType,
                   Long sourceId,
                   String sourceNo,
                   LocalDate bizDate,
                   LocalDate dueDate,
                   BigDecimal netAmount,
                   BigDecimal taxAmount,
                   BigDecimal totalAmount,
                   String remark) {
        this.partnerId = partnerId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.sourceNo = sourceNo;
        this.bizDate = bizDate;
        this.dueDate = dueDate;
        this.netAmount = netAmount;
        this.taxAmount = taxAmount;
        this.totalAmount = totalAmount;
        this.remark = remark;
    }

    /** Records money paid against this debt and re-derives the status. */
    public void settle(BigDecimal amount) {
        this.amountPaid = this.amountPaid.add(amount);
        this.status = LedgerStatus.of(this.totalAmount, this.amountPaid);
    }

    /** The source document was reversed, so the debt no longer counts. */
    public void voidOut() {
        this.status = LedgerStatus.VOID;
    }

    /** What is still owed. */
    public BigDecimal balance() {
        return totalAmount.subtract(amountPaid);
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getSourceNo() {
        return sourceNo;
    }

    public LocalDate getBizDate() {
        return bizDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
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

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public LedgerStatus getStatus() {
        return status;
    }

    public String getRemark() {
        return remark;
    }
}
