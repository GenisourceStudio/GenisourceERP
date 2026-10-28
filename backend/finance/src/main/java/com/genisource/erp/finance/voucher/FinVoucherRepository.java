package com.genisource.erp.finance.voucher;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinVoucherRepository extends JpaRepository<FinVoucher, Long>,
        JpaSpecificationExecutor<FinVoucher> {

    Optional<FinVoucher> findBySourceTypeAndSourceId(String sourceType, Long sourceId);

    /** Loads the lines with the header; one query, because a voucher without lines is a rumour. */
    @Query("select distinct v from FinVoucher v left join fetch v.lines where v.id = :id")
    Optional<FinVoucher> findWithLinesById(@Param("id") Long id);

    /** Finds the reversal of a voucher, so reversing twice cannot duplicate it. */
    Optional<FinVoucher> findByReversalOf(Long reversalOf);
}
