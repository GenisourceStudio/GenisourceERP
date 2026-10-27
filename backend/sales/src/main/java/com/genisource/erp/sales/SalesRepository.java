package com.genisource.erp.sales;

import com.genisource.erp.platform.common.DocumentLifecycle;
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

    /**
     * Open orders of one kind, with their lines loaded.
     *
     * <p>{@code distinct} because the fetch join multiplies the header across its lines;
     * without it one order for five items comes back five times and MRP would count the
     * demand five times over.
     */
    @org.springframework.data.jpa.repository.Query("""
            select distinct d from SalesDocument d left join fetch d.lines
            where d.docType = :docType and d.status in :statuses
            """)
    List<SalesDocument> findOpenWithLines(SalesDocType docType,
                                          java.util.Collection<DocumentLifecycle> statuses);
}
