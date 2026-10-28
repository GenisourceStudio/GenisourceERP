package com.genisource.erp.finance.voucher;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * The read side of the ledger.
 *
 * <p>Every aggregation here sums over counted vouchers: everything that is not a
 * draft. A reversed voucher counts, and so does the red-letter voucher that reverses
 * it — the two cancel, which is what red-letter reversal means. The rule a reader has
 * to hold is one sentence long: drafts are not history yet.
 */
public interface FinVoucherLineRepository extends JpaRepository<FinVoucherLine, Long> {

    boolean existsByAccountId(Long accountId);

    /**
     * Cumulative debit and credit per account, from the beginning of time to a date.
     * This is what every balance-facing report is built from.
     */
    @Query("""
            select l.accountId, sum(l.debit), sum(l.credit)
            from FinVoucherLine l
            where l.voucher.status in :statuses and l.voucher.bizDate <= :toDate
            group by l.accountId
            """)
    List<Object[]> balanceByAccount(@Param("statuses") List<VoucherStatus> statuses,
                                    @Param("toDate") LocalDate toDate);

    /** The same sums, but only inside a period — what the income statement reads. */
    @Query("""
            select l.accountId, sum(l.debit), sum(l.credit)
            from FinVoucherLine l
            where l.voucher.status in :statuses and l.voucher.bizDate between :from and :to
            group by l.accountId
            """)
    List<Object[]> movementByAccount(@Param("statuses") List<VoucherStatus> statuses,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /**
     * Ledger detail: the lines of one account inside a period, oldest first, carrying
     * the voucher header so the running balance can be computed in one pass.
     */
    @Query("""
            select v.bizDate, v.voucherNo, v.voucherType, l
            from FinVoucherLine l join l.voucher v
            where v.status in :statuses and l.accountId = :accountId
              and v.bizDate between :from and :to
            order by v.bizDate asc, v.voucherNo asc, l.lineNo asc
            """)
    List<Object[]> ledgerLines(@Param("statuses") List<VoucherStatus> statuses,
                               @Param("accountId") Long accountId,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

    /** Opening balance of one account: everything posted before the period starts. */
    @Query("""
            select coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0)
            from FinVoucherLine l
            where l.voucher.status in :statuses and l.accountId = :accountId
              and l.voucher.bizDate < :from
            """)
    List<Object[]> openingBalance(@Param("statuses") List<VoucherStatus> statuses,
                                  @Param("accountId") Long accountId,
                                  @Param("from") LocalDate from);

    /**
     * Opening balances of every account at once. One query for the whole trial balance
     * rather than one per account, which is the difference between a report that
     * opens instantly and one that opens after a hundred round trips.
     */
    @Query("""
            select l.accountId, coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0)
            from FinVoucherLine l
            where l.voucher.status in :statuses and l.voucher.bizDate < :from
            group by l.accountId
            """)
    List<Object[]> openingByAccount(@Param("statuses") List<VoucherStatus> statuses,
                                    @Param("from") LocalDate from);

    /**
     * The counterpart of every cash movement. For each POSTED voucher that touched a
     * cash account, the other lines of the same voucher: what money came from or went
     * to. Classification (operating / investing / financing) is decided from the
     * counterpart account's type, which keeps the cash flow statement working for a
     * chart the factory has renumbered.
     */
    @Query("""
            select other.accountId, other.debit, other.credit, other.partnerId
            from FinVoucherLine cashLine, FinVoucherLine other
            where other.voucher.id = cashLine.voucher.id
              and other.id <> cashLine.id
              and cashLine.accountId in :cashAccountIds
              and cashLine.voucher.status in :statuses
              and cashLine.voucher.bizDate between :from and :to
            """)
    List<Object[]> cashCounterparts(@Param("statuses") List<VoucherStatus> statuses,
                                    @Param("cashAccountIds") List<Long> cashAccountIds,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    /** How much one partner was debited or credited on one account, for sub-ledgers. */
    @Query("""
            select l.partnerId, sum(l.debit), sum(l.credit)
            from FinVoucherLine l
            where l.voucher.status in :statuses and l.accountId = :accountId
              and l.partnerId is not null and l.voucher.bizDate <= :toDate
            group by l.partnerId
            """)
    List<Object[]> balanceByPartner(@Param("statuses") List<VoucherStatus> statuses,
                                    @Param("accountId") Long accountId,
                                    @Param("toDate") LocalDate toDate);
}
