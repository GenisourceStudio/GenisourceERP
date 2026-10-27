package com.genisource.erp.purchase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseLineRepository extends JpaRepository<PurchaseDocumentLine, Long> {

    /** Lines that came from one line of the document before, e.g. order lines from a requisition. */
    List<PurchaseDocumentLine> findBySourceLineId(Long sourceLineId);
}
