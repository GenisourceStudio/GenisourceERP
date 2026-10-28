package com.genisource.erp.finance.account;

/**
 * What kind of thing an account measures.
 *
 * <p>The type is not cosmetic: the balance sheet asks for ASSET, LIABILITY and EQUITY,
 * the income statement asks for REVENUE, EXPENSE and COST, and a report that had to
 * infer the difference from the account code would break the first time a factory
 * renumbered its chart.
 *
 * <p>COST (生产成本) sits apart from EXPENSE because it is where inventory value waits
 * while goods are half-built. It is neither a profit-and-loss line nor a claim on the
 * business until the goods are finished and sold.
 */
public enum AccountType {
    ASSET(BalanceDirection.DEBIT),
    LIABILITY(BalanceDirection.CREDIT),
    EQUITY(BalanceDirection.CREDIT),
    REVENUE(BalanceDirection.CREDIT),
    EXPENSE(BalanceDirection.DEBIT),
    COST(BalanceDirection.DEBIT);

    private final BalanceDirection normal;

    AccountType(BalanceDirection normal) {
        this.normal = normal;
    }

    /** The side that increases this type of account. */
    public BalanceDirection normal() {
        return normal;
    }
}
