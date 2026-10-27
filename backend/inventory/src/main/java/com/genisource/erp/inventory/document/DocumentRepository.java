package com.genisource.erp.inventory.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<StockDocument, Long>,
        JpaSpecificationExecutor<StockDocument> {

    @Query("select d from StockDocument d left join fetch d.lines where d.id = :id")
    Optional<StockDocument> findWithLinesById(Long id);

    Optional<StockDocument> findByDocNo(String docNo);

    boolean existsByDocNo(String docNo);

    long countByReversalOf(Long documentId);

    /** Documents that have already been posted, used to forbid editing a frozen header. */
    List<StockDocument> findByStatus(DocumentStatus status);

    /** What one business document brought about, used when that document is reversed. */
    List<StockDocument> findBySourceTypeAndSourceIdOrderByIdAsc(String sourceType, String sourceId);
}
