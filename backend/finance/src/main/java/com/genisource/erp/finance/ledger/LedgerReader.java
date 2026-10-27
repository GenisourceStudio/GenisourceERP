package com.genisource.erp.finance.ledger;

import com.genisource.erp.masterdata.partner.Partner;
import com.genisource.erp.masterdata.partner.PartnerRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns ledger rows into what the screen needs: names instead of ids, a balance that
 * is arithmetic rather than a stored field, and a summary that is counted against
 * today.
 *
 * <p>Shared by both sides because the shape of the answer does not depend on whether
 * the money is coming in or going out.
 */
@Component
public class LedgerReader {

    private final PartnerRepository partners;

    public LedgerReader(PartnerRepository partners) {
        this.partners = partners;
    }

    public List<LedgerDtos.LedgerView> views(List<? extends LedgerRow> rows) {
        return views(rows, LocalDate.now());
    }

    public List<LedgerDtos.LedgerView> views(List<? extends LedgerRow> rows, LocalDate today) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Partner> partnerById = partners
                .findAllById(rows.stream().map(LedgerRow::getPartnerId).distinct().toList())
                .stream().collect(Collectors.toMap(Partner::getId, Function.identity()));
        return rows.stream().map(row -> view(row, partnerById.get(row.getPartnerId()), today)).toList();
    }

    private LedgerDtos.LedgerView view(LedgerRow row, Partner partner, LocalDate today) {
        return new LedgerDtos.LedgerView(
                row.getId(),
                row.getPartnerId(),
                partner == null ? null : partner.getCode(),
                partner == null ? null : partner.getName(),
                row.getSourceType(),
                row.getSourceId(),
                row.getSourceNo(),
                row.getBizDate(),
                row.getDueDate(),
                row.getNetAmount(),
                row.getTaxAmount(),
                row.getTotalAmount(),
                row.getAmountPaid(),
                row.getTotalAmount().subtract(row.getAmountPaid()),
                row.getStatus(),
                isOverdue(row, today),
                row.getRemark(),
                row.getRowVersion());
    }

    private static boolean isOverdue(LedgerRow row, LocalDate today) {
        return row.getStatus().isOpen()
                && row.getDueDate() != null
                && row.getDueDate().isBefore(today);
    }

    /**
     * Totals over every row, not just the page on screen.
     *
     * @param rows every row the caller selected, which may be more than one page
     */
    public LedgerDtos.LedgerSummary summary(List<? extends LedgerRow> rows) {
        return summary(rows, LocalDate.now());
    }

    public LedgerDtos.LedgerSummary summary(List<? extends LedgerRow> rows, LocalDate today) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        long open = 0;
        long overdueCount = 0;
        for (LedgerRow row : rows) {
            total = total.add(row.getTotalAmount());
            paid = paid.add(row.getAmountPaid());
            if (row.getStatus().isOpen()) {
                open++;
                if (isOverdue(row, today)) {
                    overdueCount++;
                    overdue = overdue.add(row.getTotalAmount().subtract(row.getAmountPaid()));
                }
            }
        }
        return new LedgerDtos.LedgerSummary(total, paid, total.subtract(paid), overdue, open,
                overdueCount);
    }

    /** Distinct partner ids on a set of rows, for callers that need to resolve names once. */
    static List<Long> partnerIds(List<? extends LedgerRow> rows) {
        return rows.stream().map(LedgerRow::getPartnerId).filter(Objects::nonNull).distinct().toList();
    }
}
