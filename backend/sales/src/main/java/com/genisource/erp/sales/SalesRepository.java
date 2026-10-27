package com.genisource.erp.sales;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SalesRepository extends JpaRepository<SalesDocument, Long>,
        JpaSpecificationExecutor<SalesDocument> {

    Optional<SalesDocument> findByDocNo(String docNo);

    /** A document together with its lines, so a posting never sees a half-loaded header. */
    @org.springframework.data.jpa.repository.Query(
            "select d from SalesDocument d left join fetch d.lines where d.id = :id")
    Optional<SalesDocument> findWithLinesById(Long id);

    boolean existsByDocNo(String docNo);

    /** What a document was converted from, used when the conversion is undone. */
    List<SalesDocument> findBySourceTypeAndSourceId(String sourceType, String sourceId);
}
