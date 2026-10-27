package com.genisource.erp.finance.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a receivable and a payable have in common.
 *
 * <p>The two tables are deliberately separate — a debt and a claim are different
 * things to everybody who works with them, and merging them behind a sign would make
 * every query and every screen carry a direction flag. But the code that reads them
 * out, sums them and decides whether they are overdue is identical, and an interface
 * lets that code exist once.
 */
public interface LedgerRow {

    Long getId();

    Long getPartnerId();

    String getSourceType();

    Long getSourceId();

    String getSourceNo();

    LocalDate getBizDate();

    LocalDate getDueDate();

    BigDecimal getNetAmount();

    BigDecimal getTaxAmount();

    BigDecimal getTotalAmount();

    BigDecimal getAmountPaid();

    LedgerStatus getStatus();

    String getRemark();

    long getRowVersion();
}
