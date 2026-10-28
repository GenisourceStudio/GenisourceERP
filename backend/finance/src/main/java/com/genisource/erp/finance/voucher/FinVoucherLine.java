package com.genisource.erp.finance.voucher;

import com.genisource.erp.platform.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * One side of one account inside one voucher.
 *
 * <p>A line carries a debit <em>or</em> a credit, never both and never neither — the
 * database enforces that with a CHECK, because it is the invariant every report quietly
 * assumes and the one a sloppy import would break first.
 *
 * <p>{@code partner_id} is the sub-ledger dimension: 1122 应收账款 is one account, but
 * "how much does 南方机柜 owe" is asked per partner. Splitting by partner at line level
 * keeps the trial balance a single SUM while still answering who-owes-what.
 */
@Entity
@Table(name = "fin_voucher_line", uniqueConstraints = @UniqueConstraint(
        name = "uq_fin_voucher_line_no", columnNames = {"voucher_id", "line_no"}))
public class FinVoucherLine extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_fin_voucher_line_voucher"))
    private FinVoucher voucher;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "debit", nullable = false, precision = 18, scale = 4)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(name = "credit", nullable = false, precision = 18, scale = 4)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(name = "partner_id")
    private Long partnerId;

    @Column(name = "summary", length = 255)
    private String summary;

    protected FinVoucherLine() {
    }

    public FinVoucherLine(int lineNo, Long accountId, BigDecimal debit, BigDecimal credit,
                          Long partnerId, String summary) {
        this.lineNo = lineNo;
        this.accountId = accountId;
        this.debit = debit;
        this.credit = credit;
        this.partnerId = partnerId;
        this.summary = summary;
    }

    /** Takes another line's user-visible content, keeping this row's identity. */
    public void copyContentFrom(FinVoucherLine other) {
        this.accountId = other.accountId;
        this.debit = other.debit;
        this.credit = other.credit;
        this.partnerId = other.partnerId;
        this.summary = other.summary;
    }

    void attachTo(FinVoucher voucher) {
        this.voucher = voucher;
    }

    public FinVoucher getVoucher() {
        return voucher;
    }

    public int getLineNo() {
        return lineNo;
    }

    public Long getAccountId() {
        return accountId;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public Long getPartnerId() {
        return partnerId;
    }

    public String getSummary() {
        return summary;
    }
}
