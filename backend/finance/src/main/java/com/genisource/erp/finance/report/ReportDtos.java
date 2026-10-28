package com.genisource.erp.finance.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Wire shapes for everything the ledger can answer.
 *
 * <p>Balances appear as a debit and a credit pair rather than one signed number: the
 * direction lives on the account, and a report that re-signs amounts must first agree
 * with the chart on which side is positive. Showing both sides is how the printed
 * trial balance has always looked, and it makes a wrong posting visible instead of
 * absorbed.
 */
public final class ReportDtos {

    private ReportDtos() {
    }

    /** One account on the trial balance. */
    public record TrialBalanceRow(
            Long accountId,
            String code,
            String name,
            String accountType,
            String balanceDirection,
            BigDecimal openingDebit,
            BigDecimal openingCredit,
            BigDecimal periodDebit,
            BigDecimal periodCredit,
            BigDecimal closingDebit,
            BigDecimal closingCredit) {
    }

    public record TrialBalance(
            LocalDate from,
            LocalDate to,
            List<TrialBalanceRow> rows,
            BigDecimal totalOpeningDebit,
            BigDecimal totalOpeningCredit,
            BigDecimal totalPeriodDebit,
            BigDecimal totalPeriodCredit,
            BigDecimal totalClosingDebit,
            BigDecimal totalClosingCredit,
            /** Zero when the books balance; anything else is the size of the problem. */
            BigDecimal closingDifference) {
    }

    /** One entry in an account's detail ledger, with the running balance so far. */
    public record LedgerEntry(
            String voucherNo,
            String voucherType,
            LocalDate bizDate,
            int lineNo,
            BigDecimal debit,
            BigDecimal credit,
            String direction,
            BigDecimal balance,
            String summary) {
    }

    public record AccountLedger(
            Long accountId,
            String code,
            String name,
            String balanceDirection,
            BigDecimal openingBalance,
            List<LedgerEntry> entries,
            BigDecimal periodDebit,
            BigDecimal periodCredit,
            BigDecimal closingBalance) {
    }

    /** One account on a statement, at its closing balance. */
    public record StatementLine(
            Long accountId,
            String code,
            String name,
            BigDecimal amount) {
    }

    public record BalanceSheet(
            LocalDate to,
            List<StatementLine> assets,
            List<StatementLine> liabilities,
            List<StatementLine> equity,
            /** Revenue minus cost and expense for the year, not yet closed into profit. */
            BigDecimal undistributedProfit,
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal totalEquity,
            /** Zero when assets equal claims; anything else means the chart is mis-pointed. */
            BigDecimal difference) {
    }

    public record IncomeStatement(
            LocalDate from,
            LocalDate to,
            List<StatementLine> revenue,
            List<StatementLine> cost,
            List<StatementLine> expense,
            BigDecimal totalRevenue,
            BigDecimal totalCost,
            BigDecimal totalExpense,
            BigDecimal profit) {
    }

    /** One bucket of the cash flow statement, in and out. */
    public record CashFlowSection(
            String section,
            BigDecimal inflow,
            BigDecimal outflow,
            BigDecimal net) {
    }

    public record CashFlow(
            LocalDate from,
            LocalDate to,
            BigDecimal openingCash,
            BigDecimal closingCash,
            List<CashFlowSection> sections) {
    }
}
