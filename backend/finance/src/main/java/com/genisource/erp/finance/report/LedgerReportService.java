package com.genisource.erp.finance.report;

import com.genisource.erp.finance.account.AccountType;
import com.genisource.erp.finance.account.BalanceDirection;
import com.genisource.erp.finance.account.FinAccount;
import com.genisource.erp.finance.account.FinAccountRepository;
import com.genisource.erp.finance.voucher.FinVoucherLine;
import com.genisource.erp.finance.voucher.FinVoucherLineRepository;
import com.genisource.erp.finance.voucher.VoucherStatus;
import com.genisource.erp.finance.voucher.VoucherType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The general ledger, read back.
 *
 * <p>Three questions, three shapes of the same arithmetic: what is the balance of
 * every account (trial balance), what happened to one account (detail ledger), and
 * what do those numbers mean as statements (balance sheet, income statement, cash
 * flow).
 *
 * <p>Everything here reads POSTED vouchers only. A draft is a sentence nobody has
 * agreed on yet, and a reversed voucher is one that was agreed on and then cancelled —
 * it stops counting, and the red-letter voucher that replaced it counts instead. One
 * rule, applied everywhere, is what makes the books reconcilable.
 */
@Service
public class LedgerReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * The vouchers a report sums over: everything that is not a draft. A reversed
     * voucher counts because its red-letter reversal counts too, and the two cancel.
     */
    private static final List<VoucherStatus> COUNTED = VoucherStatus.counted();

    private final FinAccountRepository accounts;
    private final FinVoucherLineRepository lines;

    public LedgerReportService(FinAccountRepository accounts, FinVoucherLineRepository lines) {
        this.accounts = accounts;
        this.lines = lines;
    }

    // ------------------------------------------------------------ trial balance

    /**
     * The trial balance: every account, its opening balance, what the period did to
     * it, and where it ended up. The closing totals must agree — that is the whole
     * point of printing it.
     */
    @Transactional(readOnly = true)
    public ReportDtos.TrialBalance trialBalance(LocalDate from, LocalDate to) {
        Map<Long, BigDecimal[]> opening = sumByAccount(
                lines.openingByAccount(COUNTED, from));
        Map<Long, BigDecimal[]> period = sumByAccount(
                lines.movementByAccount(COUNTED, from, to));

        List<FinAccount> chart = accounts.findAllByOrderByCodeAsc();
        List<ReportDtos.TrialBalanceRow> rows = new ArrayList<>();
        BigDecimal openingDebit = ZERO;
        BigDecimal openingCredit = ZERO;
        BigDecimal periodDebit = ZERO;
        BigDecimal periodCredit = ZERO;
        BigDecimal closingDebit = ZERO;
        BigDecimal closingCredit = ZERO;

        for (FinAccount account : chart) {
            BigDecimal[] open = opening.getOrDefault(account.getId(), new BigDecimal[]{ZERO, ZERO});
            BigDecimal[] move = period.getOrDefault(account.getId(), new BigDecimal[]{ZERO, ZERO});
            BigDecimal raw = open[0].subtract(open[1]).add(move[0]).subtract(move[1]);
            // The balance in the account's own direction: positive means the account
            // sits on its normal side. An asset overdrawn at the bank is a credit
            // balance, and showing it as zero would lose the overdraft.
            BigDecimal signed = account.getBalanceDirection() == BalanceDirection.DEBIT
                    ? raw : raw.negate();
            // A balance sits on the side the account normally lives on. A liability
            // with a credit balance belongs in the credit column, and a revenue
            // account's balance belongs in the credit column too — putting it in the
            // debit column would make the trial balance disagree with itself.
            boolean debitNormal = account.getBalanceDirection() == BalanceDirection.DEBIT;
            BigDecimal closeDebit = ZERO;
            BigDecimal closeCredit = ZERO;
            if (signed.signum() > 0) {
                if (debitNormal) {
                    closeDebit = signed;
                } else {
                    closeCredit = signed;
                }
            } else if (signed.signum() < 0) {
                if (debitNormal) {
                    closeCredit = signed.negate();
                } else {
                    closeDebit = signed.negate();
                }
            }

            openingDebit = openingDebit.add(open[0]);
            openingCredit = openingCredit.add(open[1]);
            periodDebit = periodDebit.add(move[0]);
            periodCredit = periodCredit.add(move[1]);
            closingDebit = closingDebit.add(closeDebit);
            closingCredit = closingCredit.add(closeCredit);

            if (open[0].signum() == 0 && open[1].signum() == 0
                    && move[0].signum() == 0 && move[1].signum() == 0) {
                continue;
            }
            rows.add(new ReportDtos.TrialBalanceRow(
                    account.getId(), account.getCode(), account.getName(),
                    account.getAccountType().name(), account.getBalanceDirection().name(),
                    open[0], open[1], move[0], move[1], closeDebit, closeCredit));
        }

        return new ReportDtos.TrialBalance(from, to, rows,
                openingDebit, openingCredit, periodDebit, periodCredit,
                closingDebit, closingCredit, closingDebit.subtract(closingCredit));
    }

    // ---------------------------------------------------------- account ledger

    /**
     * One account's story between two dates: every posting, oldest first, with the
     * balance running alongside. The running balance is computed here rather than
     * stored, because a stored running balance is a second fact to keep true.
     */
    @Transactional(readOnly = true)
    public ReportDtos.AccountLedger accountLedger(Long accountId, LocalDate from, LocalDate to) {
        FinAccount account = accounts.findById(accountId)
                .orElseThrow(() -> com.genisource.erp.platform.common.ApiException
                        .notFound("Account " + accountId + " does not exist"));
        BigDecimal opening = openingBalanceOf(accountId, from, account.getBalanceDirection());
        List<ReportDtos.LedgerEntry> entries = new ArrayList<>();
        BigDecimal running = opening;
        BigDecimal debit = ZERO;
        BigDecimal credit = ZERO;

        for (Object[] row : lines.ledgerLines(COUNTED, accountId, from, to)) {
            LocalDate bizDate = (LocalDate) row[0];
            String voucherNo = (String) row[1];
            VoucherType voucherType = (VoucherType) row[2];
            FinVoucherLine line = (FinVoucherLine) row[3];
            BigDecimal lineDebit = line.getDebit();
            BigDecimal lineCredit = line.getCredit();
            debit = debit.add(lineDebit);
            credit = credit.add(lineCredit);
            running = running.add(
                    account.getBalanceDirection() == BalanceDirection.DEBIT
                            ? lineDebit.subtract(lineCredit)
                            : lineCredit.subtract(lineDebit));
            entries.add(new ReportDtos.LedgerEntry(
                    voucherNo, voucherType.name(), bizDate, line.getLineNo(),
                    lineDebit, lineCredit, running.signum() < 0 ? "借" : "贷",
                    running, line.getSummary()));
        }

        return new ReportDtos.AccountLedger(
                account.getId(), account.getCode(), account.getName(),
                account.getBalanceDirection().name(), opening, entries,
                debit, credit, running);
    }

    private BigDecimal openingBalanceOf(Long accountId, LocalDate from, BalanceDirection direction) {
        List<Object[]> rows = lines.openingBalance(COUNTED, accountId, from);
        BigDecimal debit = rows.isEmpty() ? ZERO : toDecimal(rows.get(0)[0]);
        BigDecimal credit = rows.isEmpty() ? ZERO : toDecimal(rows.get(0)[1]);
        BigDecimal net = debit.subtract(credit);
        return direction == BalanceDirection.DEBIT ? net : net.negate();
    }

    // ------------------------------------------------------------- statements

    /**
     * The balance sheet at a date: what the business owns, what it owes, and what the
     * owners have in it.
     *
     * <p>Profit for the year has not been closed into equity yet, so it appears as a
     * separate line on the equity side. Leaving it out would make assets and claims
     * differ by exactly the year's profit, and a balance sheet that does not balance
     * is a report nobody trusts.
     */
    @Transactional(readOnly = true)
    public ReportDtos.BalanceSheet balanceSheet(LocalDate to) {
        LocalDate yearStart = to.withDayOfYear(1);
        Map<Long, BigDecimal[]> closing = sumByAccount(
                lines.balanceByAccount(COUNTED, to));
        Map<Long, BigDecimal[]> period = sumByAccount(
                lines.movementByAccount(COUNTED, yearStart, to));

        List<ReportDtos.StatementLine> assets = new ArrayList<>();
        List<ReportDtos.StatementLine> liabilities = new ArrayList<>();
        List<ReportDtos.StatementLine> equity = new ArrayList<>();

        BigDecimal totalAssets = ZERO;
        BigDecimal totalLiabilities = ZERO;
        BigDecimal totalEquity = ZERO;
        BigDecimal revenue = ZERO;
        BigDecimal cost = ZERO;
        BigDecimal expense = ZERO;

        for (FinAccount account : accounts.findAllByOrderByCodeAsc()) {
            BigDecimal[] sums = closing.getOrDefault(account.getId(), new BigDecimal[]{ZERO, ZERO});
            BigDecimal net = sums[0].subtract(sums[1]);
            BigDecimal signed = account.getBalanceDirection() == BalanceDirection.DEBIT
                    ? net : net.negate();
            if (signed.signum() == 0) {
                continue;
            }
            switch (account.getAccountType()) {
                case ASSET -> {
                    assets.add(line(account, signed));
                    totalAssets = totalAssets.add(signed);
                }
                case LIABILITY -> {
                    liabilities.add(line(account, signed));
                    totalLiabilities = totalLiabilities.add(signed);
                }
                case EQUITY -> {
                    equity.add(line(account, signed));
                    totalEquity = totalEquity.add(signed);
                }
                case REVENUE -> {
                    BigDecimal[] move = period.getOrDefault(account.getId(),
                            new BigDecimal[]{ZERO, ZERO});
                    revenue = revenue.add(move[1].subtract(move[0]));
                }
                case EXPENSE -> {
                    BigDecimal[] move = period.getOrDefault(account.getId(),
                            new BigDecimal[]{ZERO, ZERO});
                    expense = expense.add(move[0].subtract(move[1]));
                }
                case COST -> {
                    BigDecimal[] move = period.getOrDefault(account.getId(),
                            new BigDecimal[]{ZERO, ZERO});
                    cost = cost.add(move[0].subtract(move[1]));
                }
            }
        }

        BigDecimal undistributed = revenue.subtract(cost).subtract(expense);
        BigDecimal totalClaims = totalLiabilities.add(totalEquity).add(undistributed);
        return new ReportDtos.BalanceSheet(to, assets, liabilities, equity, undistributed,
                totalAssets, totalLiabilities, totalEquity, totalAssets.subtract(totalClaims));
    }

    /**
     * The income statement for a period: what was earned, what it cost, what was left.
     *
     * <p>Amounts are the period's movements, not balances. A balance sheet would tell
     * you what the year looks like at one instant; this tells you what the year did.
     */
    @Transactional(readOnly = true)
    public ReportDtos.IncomeStatement incomeStatement(LocalDate from, LocalDate to) {
        Map<Long, BigDecimal[]> period = sumByAccount(
                lines.movementByAccount(COUNTED, from, to));
        List<ReportDtos.StatementLine> revenue = new ArrayList<>();
        List<ReportDtos.StatementLine> cost = new ArrayList<>();
        List<ReportDtos.StatementLine> expense = new ArrayList<>();
        BigDecimal totalRevenue = ZERO;
        BigDecimal totalCost = ZERO;
        BigDecimal totalExpense = ZERO;

        for (FinAccount account : accounts.findAllByOrderByCodeAsc()) {
            BigDecimal[] sums = period.getOrDefault(account.getId(), new BigDecimal[]{ZERO, ZERO});
            BigDecimal net = sums[0].subtract(sums[1]);
            BigDecimal signed = account.getBalanceDirection() == BalanceDirection.DEBIT
                    ? net : net.negate();
            if (signed.signum() == 0) {
                continue;
            }
            switch (account.getAccountType()) {
                case REVENUE -> {
                    revenue.add(line(account, signed));
                    totalRevenue = totalRevenue.add(signed);
                }
                case EXPENSE -> {
                    expense.add(line(account, signed));
                    totalExpense = totalExpense.add(signed);
                }
                case COST -> {
                    cost.add(line(account, signed));
                    totalCost = totalCost.add(signed);
                }
                default -> {
                    // Assets, liabilities and equity say nothing about this period's
                    // profit; they are the balance sheet's business.
                }
            }
        }
        return new ReportDtos.IncomeStatement(from, to, revenue, cost, expense,
                totalRevenue, totalCost, totalExpense,
                totalRevenue.subtract(totalCost).subtract(totalExpense));
    }

    /**
     * The cash flow statement for a period: where the money came from and where it went.
     *
     * <p>Each cash movement is classified by what it was for, read from the account on
     * the other side of the voucher: a counterpart in revenue, cost or expense is
     * operating; a counterpart in a long-term asset is investing; a counterpart in
     * equity or a liability is financing. A counterpart the chart does not explain
     * lands in "other" rather than being quietly dropped — a cash flow statement that
     * silently loses money is worse than one that admits it cannot classify something.
     *
     * <p>This is a direct-method statement built from the vouchers, not the
     * standard-indirect reconciliation; it will not match a statutory cash flow
     * statement line for line, and it does not claim to.
     */
    @Transactional(readOnly = true)
    public ReportDtos.CashFlow cashFlow(LocalDate from, LocalDate to) {
        List<FinAccount> cash = accounts.findAllByOrderByCodeAsc().stream()
                .filter(account -> account.getBalanceDirection() == BalanceDirection.DEBIT
                        && (account.getSystemRole() == com.genisource.erp.finance.account.AccountRole.CASH
                        || account.getSystemRole() == com.genisource.erp.finance.account.AccountRole.BANK))
                .toList();
        List<Long> cashIds = cash.stream().map(FinAccount::getId).toList();
        Map<Long, FinAccount> byId = new HashMap<>();
        accounts.findAllById(cashIds).forEach(account -> byId.put(account.getId(), account));

        BigDecimal operatingIn = ZERO;
        BigDecimal operatingOut = ZERO;
        BigDecimal investingIn = ZERO;
        BigDecimal investingOut = ZERO;
        BigDecimal financingIn = ZERO;
        BigDecimal financingOut = ZERO;
        BigDecimal otherIn = ZERO;
        BigDecimal otherOut = ZERO;

        if (!cashIds.isEmpty()) {
            for (Object[] row : lines.cashCounterparts(COUNTED, cashIds, from, to)) {
                Long counterpartId = (Long) row[0];
                BigDecimal debit = (BigDecimal) row[1];
                BigDecimal credit = (BigDecimal) row[2];
                FinAccount counterpart = byId.get(counterpartId);
                AccountType type = counterpart == null ? null : counterpart.getAccountType();
                boolean cashIncreased = debit.signum() > 0;
                BigDecimal amount = cashIncreased ? debit : credit;
                if (amount.signum() == 0) {
                    continue;
                }
                if (type == null) {
                    // An account the chart does not explain: the money moved, and
                    // admitting it is unclassified beats dropping it.
                    if (cashIncreased) {
                        otherIn = otherIn.add(amount);
                    } else {
                        otherOut = otherOut.add(amount);
                    }
                    continue;
                }
                switch (type) {
                    case REVENUE, EXPENSE, COST -> {
                        if (cashIncreased) {
                            operatingIn = operatingIn.add(amount);
                        } else {
                            operatingOut = operatingOut.add(amount);
                        }
                    }
                    case ASSET -> {
                        if (cashIncreased) {
                            investingOut = investingOut.add(amount);
                        } else {
                            investingIn = investingIn.add(amount);
                        }
                    }
                    case LIABILITY, EQUITY -> {
                        if (cashIncreased) {
                            financingIn = financingIn.add(amount);
                        } else {
                            financingOut = financingOut.add(amount);
                        }
                    }
                }
            }
        }

        BigDecimal opening = cashBalance(cashIds, from);
        BigDecimal closing = cashBalance(cashIds, to);
        List<ReportDtos.CashFlowSection> sections = List.of(
                new ReportDtos.CashFlowSection("operating", operatingIn, operatingOut,
                        operatingIn.subtract(operatingOut)),
                new ReportDtos.CashFlowSection("investing", investingIn, investingOut,
                        investingIn.subtract(investingOut)),
                new ReportDtos.CashFlowSection("financing", financingIn, financingOut,
                        financingIn.subtract(financingOut)),
                new ReportDtos.CashFlowSection("other", otherIn, otherOut,
                        otherIn.subtract(otherOut)));
        return new ReportDtos.CashFlow(from, to, opening, closing, sections);
    }

    /** The cash on hand at a date: everything counted up to and including that day. */
    private BigDecimal cashBalance(List<Long> cashIds, LocalDate to) {
        if (cashIds.isEmpty()) {
            return ZERO;
        }
        BigDecimal total = ZERO;
        for (Object[] row : lines.balanceByAccount(COUNTED, to)) {
            if (cashIds.contains((Long) row[0])) {
                total = total.add(toDecimal(row[1])).subtract(toDecimal(row[2]));
            }
        }
        return total;
    }

    // ---------------------------------------------------------------- helpers

    /** Folds grouped rows into a map of account id to {debit, credit}. */
    private Map<Long, BigDecimal[]> sumByAccount(List<Object[]> rows) {
        Map<Long, BigDecimal[]> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((Long) row[0], new BigDecimal[]{toDecimal(row[1]), toDecimal(row[2])});
        }
        return result;
    }

    private ReportDtos.StatementLine line(FinAccount account, BigDecimal amount) {
        return new ReportDtos.StatementLine(account.getId(), account.getCode(),
                account.getName(), amount);
    }

    private static BigDecimal toDecimal(Object value) {
        return value == null ? ZERO : (BigDecimal) value;
    }
}
