package com.genisource.erp.finance.account;

/**
 * Which side of an account increases its balance.
 *
 * <p>Stored per account rather than derived from {@link AccountType}, because the
 * exception is common enough to matter: 应交税费 is a liability whose input-tax
 * sub-account behaves like an asset. A report that trusted the type alone would show
 * that sub-account with the wrong sign every month.
 */
public enum BalanceDirection {
    DEBIT,
    CREDIT
}
