package com.genisource.erp.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<PurchaseDocument, Long>,
        JpaSpecificationExecutor<PurchaseDocument> {

    Optional<PurchaseDocument> findByDocNo(String docNo);

    /** A document together with its lines, so a posting never sees a half-loaded header. */
    @org.springframework.data.jpa.repository.Query(
            "select d from PurchaseDocument d left join fetch d.lines where d.id = :id")
    Optional<PurchaseDocument> findWithLinesById(Long id);

    boolean existsByDocNo(String docNo);

    /** What a document was converted from, used when the conversion is undone. */
    List<PurchaseDocument> findBySourceTypeAndSourceId(String sourceType, String sourceId);
}
