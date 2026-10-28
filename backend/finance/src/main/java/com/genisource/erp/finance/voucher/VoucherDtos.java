package com.genisource.erp.finance.voucher;

import com.genisource.erp.finance.account.AccountRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class VoucherDtos {

    private VoucherDtos() {
    }

    /**
     * One line as the user typed it. Line numbers are assigned by the service in list
     * order — a client that also picks numbers would only ever disagree with itself.
     */
    public record LineRequest(
            @NotNull(message = "{validation.account.required}")
            Long accountId,

            BigDecimal debit,

            BigDecimal credit,

            Long partnerId,

            @Size(max = 255, message = "{validation.remark.size}")
            String summary) {
    }

    public record VoucherRequest(
            @NotNull(message = "{validation.voucherType.required}")
            VoucherType voucherType,

            @NotNull(message = "{validation.bizDate.required}")
            LocalDate bizDate,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark,

            @NotNull(message = "{validation.voucherLines.required}")
            List<LineRequest> lines) {
    }

    public record ReverseRequest(
            @NotNull(message = "{validation.bizDate.required}")
            LocalDate bizDate,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark) {
    }

    public record LineView(
            Long id,
            int lineNo,
            Long accountId,
            String accountCode,
            String accountName,
            BigDecimal debit,
            BigDecimal credit,
            Long partnerId,
            String partnerName,
            String summary) {
    }

    public record VoucherView(
            Long id,
            String voucherNo,
            VoucherType voucherType,
            LocalDate bizDate,
            VoucherStatus status,
            String sourceType,
            Long sourceId,
            Long reversalOf,
            /** The voucher that reversed this one, when it has been. */
            Long reversedBy,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            String postedBy,
            java.time.Instant postedAt,
            String remark,
            List<LineView> lines) {
    }

    /** Page shape matching the rest of the console: flat, no Spring types. */
    public record VoucherPage(
            List<VoucherView> content,
            int page,
            int size,
            long total,
            int totalPages,
            boolean hasNext) {
    }

    // ------------------------------------------------- automatic posting API

    /** One amount pointing at one role, used by the automatic posting rules. */
    public record RoleAmount(AccountRole role, BigDecimal amount) {
    }

    /** What a business document hands over so the ledger can write its sentence. */
    public record DocumentPosting(
            VoucherType voucherType,
            String sourceType,
            Long sourceId,
            String sourceNo,
            LocalDate bizDate,
            Long partnerId,
            String summary,
            List<RoleAmount> debits,
            List<RoleAmount> credits) {
    }
}
