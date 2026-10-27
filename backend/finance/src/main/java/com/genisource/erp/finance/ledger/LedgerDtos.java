package com.genisource.erp.finance.ledger;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Shapes shared by the receivable and the payable side. */
public final class LedgerDtos {

    private LedgerDtos() {
    }

    public record LedgerView(
            Long id,
            Long partnerId,
            String partnerCode,
            String partnerName,
            String sourceType,
            Long sourceId,
            String sourceNo,
            LocalDate bizDate,
            LocalDate dueDate,
            BigDecimal netAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            BigDecimal amountPaid,
            BigDecimal balance,
            LedgerStatus status,
            boolean overdue,
            String remark,
            long rowVersion) {
    }

    /** Money arriving against a claim, or leaving against a debt. */
    public record SettleRequest(
            @NotNull(message = "{validation.amount.required}")
            @DecimalMin(value = "0.000001", message = "{validation.amount.positive}")
            BigDecimal amount,

            @Size(max = 255, message = "{validation.remark.size}")
            String remark) {
    }

    /**
     * The totals behind a ledger screen.
     *
     * <p>Overdue is counted against today rather than stored: a bucket that had to be
     * recomputed by a job would be wrong the moment the job was late, and "how much is
     * past due" is the number people actually open this screen for.
     */
    public record LedgerSummary(
            BigDecimal totalAmount,
            BigDecimal amountPaid,
            BigDecimal balance,
            BigDecimal overdueAmount,
            long openCount,
            long overdueCount) {
    }

    public record LedgerPage(
            List<LedgerView> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            LedgerSummary summary) {
    }
}
