package com.genisource.erp.finance.voucher;

import com.genisource.erp.platform.common.TenantAppendOnlyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One movement of money against one ledger row.
 *
 * <p>The ledger row keeps only the running total ({@code amount_paid}) — that is the
 * fact M3 made authoritative. But "how did we get to 3,510 paid" is a question about
 * events, and events need their own rows: each settlement gets one, the payment voucher
 * anchors to it, and a retried settlement cannot double-book because its voucher's
 * uniqueness is the settlement's identity.
 *
 * <p>Append-only: a settlement that was wrong is corrected by another settlement, not
 * by editing money that has already moved.
 */
@Entity
@Table(name = "fin_settlement")
public class FinSettlement extends TenantAppendOnlyEntity {

    public enum Side {
        PAYABLE,
        RECEIVABLE
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 8)
    private Side side;

    @Column(name = "ledger_id", nullable = false)
    private Long ledgerId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "biz_date", nullable = false)
    private LocalDate bizDate;

    @Column(name = "remark", length = 255)
    private String remark;

    protected FinSettlement() {
    }

    public FinSettlement(Side side, Long ledgerId, BigDecimal amount, LocalDate bizDate, String remark) {
        this.side = side;
        this.ledgerId = ledgerId;
        this.amount = amount;
        this.bizDate = bizDate;
        this.remark = remark;
    }

    public Side getSide() {
        return side;
    }

    public Long getLedgerId() {
        return ledgerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getBizDate() {
        return bizDate;
    }

    public String getRemark() {
        return remark;
    }
}
