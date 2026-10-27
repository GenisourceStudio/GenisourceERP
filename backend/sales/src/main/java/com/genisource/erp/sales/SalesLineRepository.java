package com.genisource.erp.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesLineRepository extends JpaRepository<SalesDocumentLine, Long> {

    /** Lines that came from one line of the document before, e.g. order lines from a requisition. */
    List<SalesDocumentLine> findBySourceLineId(Long sourceLineId);
}
