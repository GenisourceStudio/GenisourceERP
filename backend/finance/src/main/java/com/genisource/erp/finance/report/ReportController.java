package com.genisource.erp.finance.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Reading the books.
 *
 * <p>One permission for all of it: whoever may see the money may see every view of
 * it. Splitting "may see the balance sheet" from "may see the trial balance" would
 * mean asking an accountant to pick which of their own reports they are allowed to
 * open, and the answer is always all of them.
 */
@RestController
@RequestMapping("/api/finance/reports")
@Tag(name = "Finance :: reports", description = "Trial balance, ledgers and the three statements")
public class ReportController {

    private final LedgerReportService reports;

    public ReportController(LedgerReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/trial-balance")
    @Operation(summary = "Every account's opening balance, the period's movement and the closing balance")
    @PreAuthorize("hasAuthority('finance:report:read')")
    public ReportDtos.TrialBalance trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate toDate = to == null ? LocalDate.now() : to;
        LocalDate fromDate = from == null ? toDate.withDayOfYear(1) : from;
        return reports.trialBalance(fromDate, toDate);
    }

    @GetMapping("/accounts/{accountId}/ledger")
    @Operation(summary = "One account's postings between two dates, with the running balance")
    @PreAuthorize("hasAuthority('finance:report:read')")
    public ReportDtos.AccountLedger accountLedger(
            @PathVariable Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate toDate = to == null ? LocalDate.now() : to;
        LocalDate fromDate = from == null ? toDate.withDayOfYear(1) : from;
        return reports.accountLedger(accountId, fromDate, toDate);
    }

    @GetMapping("/balance-sheet")
    @Operation(summary = "What the business owns and owes at a date")
    @PreAuthorize("hasAuthority('finance:report:read')")
    public ReportDtos.BalanceSheet balanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.balanceSheet(to == null ? LocalDate.now() : to);
    }

    @GetMapping("/income-statement")
    @Operation(summary = "What the period earned, cost and left")
    @PreAuthorize("hasAuthority('finance:report:read')")
    public ReportDtos.IncomeStatement incomeStatement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate toDate = to == null ? LocalDate.now() : to;
        LocalDate fromDate = from == null ? toDate.withDayOfYear(1) : from;
        return reports.incomeStatement(fromDate, toDate);
    }

    @GetMapping("/cash-flow")
    @Operation(summary = "Where the money came from and where it went")
    @PreAuthorize("hasAuthority('finance:report:read')")
    public ReportDtos.CashFlow cashFlow(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate toDate = to == null ? LocalDate.now() : to;
        LocalDate fromDate = from == null ? toDate.withDayOfYear(1) : from;
        return reports.cashFlow(fromDate, toDate);
    }
}
