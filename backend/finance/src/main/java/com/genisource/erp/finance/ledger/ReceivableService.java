package com.genisource.erp.finance.ledger;

import com.genisource.erp.platform.audit.AuditService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.util.List;

/**
 * The receivable side: what customers owe us.
 *
 * <p>Created when a sales shipment is posted, because that is the moment the goods
 * stopped being ours and the claim became real. Settled as customers pay.
 */
@Service
public class ReceivableService extends LedgerService<Receivable> {

    private final ReceivableRepository receivables;

    public ReceivableService(ReceivableRepository receivables, LedgerReader reader, AuditService audit,
                             com.genisource.erp.finance.voucher.VoucherService vouchers) {
        super(reader, audit, vouchers);
        this.receivables = receivables;
    }

    @Override
    protected JpaRepository<Receivable, Long> repository() {
        return receivables;
    }

    @Override
    protected JpaSpecificationExecutor<Receivable> specifications() {
        return receivables;
    }

    @Override
    protected java.util.Optional<Receivable> findBySource(String sourceType, Long sourceId) {
        return receivables.findBySourceTypeAndSourceId(sourceType, sourceId);
    }

    @Override
    protected Receivable newRow(Long partnerId,
                                String sourceType,
                                Long sourceId,
                                String sourceNo,
                                LocalDate bizDate,
                                LocalDate dueDate,
                                BigDecimal netAmount,
                                BigDecimal taxAmount,
                                BigDecimal totalAmount,
                                String remark) {
        return new Receivable(partnerId, sourceType, sourceId, sourceNo, bizDate, dueDate,
                netAmount, taxAmount, totalAmount, remark);
    }

    @Override
    protected void settle(Receivable row, BigDecimal amount) {
        row.settle(amount);
    }

    @Override
    protected void voidOut(Receivable row) {
        row.voidOut();
    }

    @Override
    protected String settleAction() {
        return "finance.receivable.collect";
    }

    @Override
    protected String voidAction() {
        return "finance.receivable.void";
    }

    @Override
    protected String tableName() {
        return "fin_receivable";
    }

    /**
     * The shipment's sentence: a claim on the customer, revenue recognised, tax to hand
     * over. The matching cost entry (借 主营业务成本 / 贷 库存) is written separately,
     * by the shipment itself, because only it knows what the goods cost — this voucher
     * only knows what they sold for.
     */
    @Override
    protected void postVoucherFor(Receivable row) {
        vouchers.postFromDocument(new com.genisource.erp.finance.voucher.VoucherDtos.DocumentPosting(
                com.genisource.erp.finance.voucher.VoucherType.SALES,
                row.getSourceType(), row.getSourceId(), row.getSourceNo(), row.getBizDate(),
                row.getPartnerId(), "发货 " + row.getSourceNo(),
                List.of(new com.genisource.erp.finance.voucher.VoucherDtos.RoleAmount(
                        com.genisource.erp.finance.account.AccountRole.AR,
                        row.getTotalAmount())),
                List.of(
                        new com.genisource.erp.finance.voucher.VoucherDtos.RoleAmount(
                                com.genisource.erp.finance.account.AccountRole.REVENUE,
                                row.getNetAmount()),
                        new com.genisource.erp.finance.voucher.VoucherDtos.RoleAmount(
                                com.genisource.erp.finance.account.AccountRole.TAX_OUTPUT,
                                row.getTaxAmount()))));
    }

    /** The shipment reversal must also take back the cost entry written beside it. */
    @Override
    protected void onLedgerVoided(Receivable row) {
        vouchers.reverseForSource(row.getSourceType() + "_COST", row.getSourceId(),
                java.time.LocalDate.now());
    }

    @Override
    protected boolean payable() {
        return false;
    }
}
