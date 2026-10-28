package com.genisource.erp.finance.ledger;

import com.genisource.erp.platform.audit.AuditService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.util.List;

/**
 * The payable side: what we owe suppliers.
 *
 * <p>Created when a purchase receipt is posted, because that is the moment goods
 * became ours and the debt became real. Settled as the finance office pays.
 */
@Service
public class PayableService extends LedgerService<Payable> {

    private final PayableRepository payables;

    public PayableService(PayableRepository payables, LedgerReader reader, AuditService audit,
                          com.genisource.erp.finance.voucher.VoucherService vouchers) {
        super(reader, audit, vouchers);
        this.payables = payables;
    }

    @Override
    protected JpaRepository<Payable, Long> repository() {
        return payables;
    }

    @Override
    protected JpaSpecificationExecutor<Payable> specifications() {
        return payables;
    }

    @Override
    protected java.util.Optional<Payable> findBySource(String sourceType, Long sourceId) {
        return payables.findBySourceTypeAndSourceId(sourceType, sourceId);
    }

    @Override
    protected Payable newRow(Long partnerId,
                             String sourceType,
                             Long sourceId,
                             String sourceNo,
                             LocalDate bizDate,
                             LocalDate dueDate,
                             BigDecimal netAmount,
                             BigDecimal taxAmount,
                             BigDecimal totalAmount,
                             String remark) {
        return new Payable(partnerId, sourceType, sourceId, sourceNo, bizDate, dueDate,
                netAmount, taxAmount, totalAmount, remark);
    }

    @Override
    protected void settle(Payable row, BigDecimal amount) {
        row.settle(amount);
    }

    @Override
    protected void voidOut(Payable row) {
        row.voidOut();
    }

    @Override
    protected String settleAction() {
        return "finance.payable.pay";
    }

    @Override
    protected String voidAction() {
        return "finance.payable.void";
    }

    @Override
    protected String tableName() {
        return "fin_payable";
    }

    /**
     * The receipt's sentence: goods and deductible tax in, supplier debt up.
     *
     * <p>The inventory debit goes to the account playing {@code INVENTORY_RAW}. A
     * receipt that mixes raw material and resale goods books the whole net amount to
     * one account — splitting per item type is costing's job, and it needs the same
     * per-line valuation the cost module will own.
     */
    @Override
    protected void postVoucherFor(Payable row) {
        vouchers.postFromDocument(new com.genisource.erp.finance.voucher.VoucherDtos.DocumentPosting(
                com.genisource.erp.finance.voucher.VoucherType.PURCHASE,
                row.getSourceType(), row.getSourceId(), row.getSourceNo(), row.getBizDate(),
                row.getPartnerId(), "收料 " + row.getSourceNo(),
                List.of(
                        new com.genisource.erp.finance.voucher.VoucherDtos.RoleAmount(
                                com.genisource.erp.finance.account.AccountRole.INVENTORY_RAW,
                                row.getNetAmount()),
                        new com.genisource.erp.finance.voucher.VoucherDtos.RoleAmount(
                                com.genisource.erp.finance.account.AccountRole.TAX_INPUT,
                                row.getTaxAmount())),
                List.of(new com.genisource.erp.finance.voucher.VoucherDtos.RoleAmount(
                        com.genisource.erp.finance.account.AccountRole.AP,
                        row.getTotalAmount()))));
    }

    @Override
    protected boolean payable() {
        return true;
    }
}
