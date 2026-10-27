package com.genisource.erp.finance.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ReceivableRepository
        extends JpaRepository<Receivable, Long>, JpaSpecificationExecutor<Receivable> {

    /**
     * The row one source document produced, if any. Used to make creation idempotent
     * and to find the row again when that document is reversed.
     */
    Optional<Receivable> findBySourceTypeAndSourceId(String sourceType, Long sourceId);
}
