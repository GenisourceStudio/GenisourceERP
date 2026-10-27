package com.genisource.erp.finance.ledger;

import com.genisource.erp.platform.audit.AuditService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The receivable side: what customers owe us.
 *
 * <p>Created when a sales shipment is posted, because that is the moment the goods
 * stopped being ours and the claim became real. Settled as customers pay.
 */
@Service
public class ReceivableService extends LedgerService<Receivable> {

    private final ReceivableRepository receivables;

    public ReceivableService(ReceivableRepository receivables, LedgerReader reader, AuditService audit) {
        super(reader, audit);
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
}
