package com.genisource.erp.finance.voucher;

import com.genisource.erp.platform.common.VersionedTenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One accounting sentence: on this date, these accounts moved these amounts, because
 * this happened.
 *
 * <p>The header carries the who-when-why; the {@link FinVoucherLine lines} carry the
 * arithmetic. Totals are re-derived from the lines rather than trusted — a total stored
 * beside its parts is a second fact to keep true, and this way a line edited in a draft
 * cannot leave a header claiming a balance the lines no longer support.
 *
 * <p>Lines are replaced by merging on {@code line_no}, never by clearing and re-adding.
 * Hibernate inserts before it deletes, so a clear-and-re-add collides with the unique
 * line number on its own table; and reassigning row ids would pull the rug out from
 * anything pointing at a line.
 */
@Entity
@Table(name = "fin_voucher", indexes = {
        @Index(name = "ix_fin_voucher_date", columnList = "tenant_id, biz_date"),
        @Index(name = "ix_fin_voucher_status", columnList = "tenant_id, status, biz_date")})
public class FinVoucher extends VersionedTenantEntity {

    @Column(name = "voucher_no", nullable = false, length = 32)
    private String voucherNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false, length = 16)
    private VoucherType voucherType;

    @Column(name = "biz_date", nullable = false)
    private LocalDate bizDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private VoucherStatus status = VoucherStatus.DRAFT;

    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "reversal_of")
    private Long reversalOf;

    @Column(name = "total_debit", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(name = "total_credit", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "posted_by", length = 64)
    private String postedBy;

    @Column(name = "remark", length = 500)
    private String remark;

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    @OrderBy("lineNo ASC")
    private final List<FinVoucherLine> lines = new ArrayList<>();

    protected FinVoucher() {
    }

    public FinVoucher(String voucherNo, VoucherType voucherType, LocalDate bizDate,
                      String sourceType, Long sourceId, String remark) {
        this.voucherNo = voucherNo;
        this.voucherType = voucherType;
        this.bizDate = bizDate;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.remark = remark;
    }

    /**
     * Replaces the lines, merging on line number: a line number present in both keeps
     * its row id and takes the new amounts, one only in the request is added, one only
     * on the voucher is removed. Renumbering that reassigns ids would orphan anything
     * that remembered a line, and insert-before-delete would trip the unique number.
     */
    public void replaceLines(List<FinVoucherLine> requested) {
        List<Integer> kept = requested.stream().map(FinVoucherLine::getLineNo).toList();
        lines.removeIf(line -> !kept.contains(line.getLineNo()));
        for (FinVoucherLine request : requested) {
            Optional<FinVoucherLine> existing = lines.stream()
                    .filter(line -> line.getLineNo() == request.getLineNo())
                    .findFirst();
            if (existing.isPresent()) {
                existing.get().copyContentFrom(request);
            } else {
                request.attachTo(this);
                lines.add(request);
            }
        }
        lines.sort(Comparator.comparingInt(FinVoucherLine::getLineNo));
        recomputeTotals();
    }

    /** Re-derives the header totals from the lines, so the header cannot disagree. */
    public void recomputeTotals() {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (FinVoucherLine line : lines) {
            debit = debit.add(line.getDebit());
            credit = credit.add(line.getCredit());
        }
        this.totalDebit = debit;
        this.totalCredit = credit;
    }

    /** The difference that must be zero before this voucher may post. */
    public BigDecimal imbalance() {
        return totalDebit.subtract(totalCredit);
    }

    public boolean balanced() {
        return imbalance().compareTo(BigDecimal.ZERO) == 0;
    }

    public void post(String by, Instant at) {
        this.status = VoucherStatus.POSTED;
        this.postedBy = by;
        this.postedAt = at;
    }

    /**
     * Marks this voucher as reversed. It keeps counting towards the books, because the
     * red-letter voucher that reverses it counts too and the two cancel — that is what
     * red-letter reversal means. What changes is that the voucher list now shows it
     * was cancelled and names the voucher that did it.
     */
    public void markReversed() {
        this.status = VoucherStatus.REVERSED;
    }

    /** A reversal is born posted: the correction is effective the moment it is saved. */
    public static FinVoucher asReversalOf(FinVoucher original, String voucherNo, LocalDate bizDate) {
        FinVoucher reversal = new FinVoucher(voucherNo, VoucherType.REVERSAL, bizDate,
                original.sourceType, original.sourceId,
                "冲销 " + original.getVoucherNo()
                        + (original.getRemark() == null ? "" : " · " + original.getRemark()));
        reversal.reversalOf = original.getId();
        List<FinVoucherLine> swapped = new ArrayList<>();
        int lineNo = 10;
        for (FinVoucherLine line : original.getLines()) {
            swapped.add(new FinVoucherLine(lineNo, line.getAccountId(),
                    line.getCredit(), line.getDebit(), line.getPartnerId(),
                    line.getSummary() == null ? null : "冲 " + line.getSummary()));
            lineNo += 10;
        }
        reversal.replaceLines(swapped);
        return reversal;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getVoucherNo() {
        return voucherNo;
    }

    public VoucherType getVoucherType() {
        return voucherType;
    }

    public LocalDate getBizDate() {
        return bizDate;
    }

    public VoucherStatus getStatus() {
        return status;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public Long getReversalOf() {
        return reversalOf;
    }

    public BigDecimal getTotalDebit() {
        return totalDebit;
    }

    public BigDecimal getTotalCredit() {
        return totalCredit;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public String getPostedBy() {
        return postedBy;
    }

    public String getRemark() {
        return remark;
    }

    public List<FinVoucherLine> getLines() {
        return lines;
    }
}
