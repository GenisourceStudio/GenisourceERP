package com.genisource.erp.finance.ledger;

import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The behaviour a receivable and a payable share.
 *
 * <p>Both sides are created by somebody else's posting, both are settled by money
 * moving, and both are voided when the document behind them is reversed. Only the
 * table and the words differ, so the behaviour sits here once and each side supplies
 * the row it stores.
 */
abstract class LedgerService<T extends LedgerRow> {

    private static final int SCALE = 4;

    private final LedgerReader reader;
    private final AuditService audit;
    /** Writes the accounting sentence that goes with each ledger event. */
    protected final com.genisource.erp.finance.voucher.VoucherService vouchers;

    protected LedgerService(LedgerReader reader, AuditService audit,
                            com.genisource.erp.finance.voucher.VoucherService vouchers) {
        this.reader = reader;
        this.audit = audit;
        this.vouchers = vouchers;
    }

    /** The repository of the concrete side. */
    protected abstract JpaRepository<T, Long> repository();

    /** The same repository seen as a specification executor. */
    protected abstract JpaSpecificationExecutor<T> specifications();

    /**
     * The row one source document produced, if any. Used to make creation idempotent
     * and to find the row again when that document is reversed.
     */
    protected abstract java.util.Optional<T> findBySource(String sourceType, Long sourceId);

    /** Builds a row of the concrete side. */
    protected abstract T newRow(Long partnerId,
                                String sourceType,
                                Long sourceId,
                                String sourceNo,
                                LocalDate bizDate,
                                LocalDate dueDate,
                                BigDecimal netAmount,
                                BigDecimal taxAmount,
                                BigDecimal totalAmount,
                                String remark);

    /** The audit event name for money moving against this side. */
    protected abstract String settleAction();

    /** The audit event name for voiding this side. */
    protected abstract String voidAction();

    // ------------------------------------------------------------------ write

    /**
     * Records what a posted trade document created.
     *
     * <p>Idempotent by construction: the same source document can only ever produce one
     * row. Without that, a retry after a timeout — or an impatient second click — would
     * book the same debt twice, and nothing downstream could tell the duplicate from
     * the real one.
     *
     * @return the id of the row, existing or new
     */
    @Transactional
    public Long createFrom(String sourceType,
                           Long sourceId,
                           String sourceNo,
                           Long partnerId,
                           LocalDate bizDate,
                           LocalDate dueDate,
                           BigDecimal netAmount,
                           BigDecimal taxAmount,
                           BigDecimal totalAmount,
                           String remark) {
        return findBySource(sourceType, sourceId)
                .map(T::getId)
                .orElseGet(() -> {
                    T row = newRow(partnerId, sourceType, sourceId, sourceNo, bizDate, dueDate,
                            netAmount, taxAmount, totalAmount, remark);
                    repository().save(row);
                    // The ledger row and its voucher are born together, in this
                    // transaction: stock moved and money was owed in the same breath,
                    // so the books cannot lag the warehouse.
                    postVoucherFor(row);
                    return row.getId();
                });
    }

    /** Records money moving against one row. */
    @Transactional
    public LedgerDtos.LedgerView settle(Long id, LedgerDtos.SettleRequest request) {
        T row = require(id);
        if (row.getStatus() == LedgerStatus.VOID) {
            throw ApiException.invalidState("This entry was voided and cannot be settled");
        }
        BigDecimal outstanding = row.getTotalAmount().subtract(row.getAmountPaid());
        BigDecimal amount = request.amount().setScale(SCALE, RoundingMode.HALF_UP);
        if (amount.compareTo(outstanding) > 0) {
            throw ApiException.badRequest("Only " + outstanding.stripTrailingZeros().toPlainString()
                    + " is still outstanding, so " + amount.stripTrailingZeros().toPlainString()
                    + " cannot be settled");
        }
        settle(row, amount);
        repository().save(row);
        // Money moved, so the books move with it: the settlement and its voucher are
        // one event seen from the ledger and from the general ledger.
        vouchers.recordSettlement(payable(), row, amount);
        audit.record(settleAction(), CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                tableName(), String.valueOf(id),
                Map.of("amount", amount.toPlainString(), "sourceNo", row.getSourceNo()));
        return reader.views(List.of(row)).get(0);
    }

    /** Concrete sides differ in how the money is applied. */
    protected abstract void settle(T row, BigDecimal amount);

    /** The source document was reversed, so what it created no longer counts. */
    @Transactional
    public void voidSource(String sourceType, Long sourceId) {
        findBySource(sourceType, sourceId).ifPresent(row -> {
            if (row.getStatus() != LedgerStatus.VOID) {
                voidOut(row);
                repository().save(row);
                // The voucher the posting wrote stops counting the same way every
                // correction does: a red-letter reversal beside it, not an edit.
                vouchers.reverseForSource(sourceType, sourceId, java.time.LocalDate.now());
                onLedgerVoided(row);
                audit.record(voidAction(), CurrentUser.id().orElse(null),
                        CurrentUser.username().orElse(null),
                        tableName(), String.valueOf(row.getId()),
                        Map.of("sourceNo", row.getSourceNo()));
            }
        });
    }

    protected abstract void voidOut(T row);

    /** Writes the voucher this ledger row implies, called once when the row is born. */
    protected abstract void postVoucherFor(T row);

    /** Which side of the trade this ledger is on; decides the settlement voucher. */
    protected abstract boolean payable();

    /** Extra vouchers beyond the trade one — e.g. a cost transfer — reversed too. */
    protected void onLedgerVoided(T row) {
    }

    protected abstract String tableName();

    // ------------------------------------------------------------------- read

    public LedgerDtos.LedgerView get(Long id) {
        return reader.views(List.of(require(id))).get(0);
    }

    public LedgerDtos.LedgerPage search(String keyword,
                                        Long partnerId,
                                        LedgerStatus status,
                                        LocalDate from,
                                        LocalDate to,
                                        boolean onlyOverdue,
                                        int page,
                                        int size) {
        LocalDate today = LocalDate.now();
        Specification<T> spec = specification(keyword, partnerId, status, from, to, onlyOverdue, today);
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by("id").descending());
        Page<T> result = specifications().findAll(spec, pageable);
        List<T> everything = specifications().findAll(spec, Sort.by("id").ascending());
        return new LedgerDtos.LedgerPage(
                reader.views(result.getContent(), today),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                reader.summary(everything, today));
    }

    private Specification<T> specification(String keyword,
                                           Long partnerId,
                                           LedgerStatus status,
                                           LocalDate from,
                                           LocalDate to,
                                           boolean onlyOverdue,
                                           LocalDate today) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (partnerId != null) {
                predicates.add(cb.equal(root.get("partnerId"), partnerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("bizDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("bizDate"), to));
            }
            if (onlyOverdue) {
                predicates.add(cb.isNotNull(root.get("dueDate")));
                predicates.add(cb.lessThan(root.get("dueDate"), today));
                predicates.add(root.get("status").in(LedgerStatus.OPEN, LedgerStatus.PARTIAL));
            }
            if (keyword != null && !keyword.isBlank()) {
                String term = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("sourceNo")), term));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    protected T require(Long id) {
        return repository().findById(id)
                .orElseThrow(() -> ApiException.notFound("Ledger entry " + id + " does not exist"));
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 200);
    }
}
