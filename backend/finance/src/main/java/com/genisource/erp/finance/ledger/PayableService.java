package com.genisource.erp.finance.ledger;

import com.genisource.erp.platform.audit.AuditService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The payable side: what we owe suppliers.
 *
 * <p>Created when a purchase receipt is posted, because that is the moment goods
 * became ours and the debt became real. Settled as the finance office pays.
 */
@Service
public class PayableService extends LedgerService<Payable> {

    private final PayableRepository payables;

    public PayableService(PayableRepository payables, LedgerReader reader, AuditService audit) {
        super(reader, audit);
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
}
