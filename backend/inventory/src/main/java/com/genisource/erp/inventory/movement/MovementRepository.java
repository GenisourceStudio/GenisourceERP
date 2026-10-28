package com.genisource.erp.inventory.movement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MovementRepository extends JpaRepository<Movement, Long>,
        JpaSpecificationExecutor<Movement> {

    List<Movement> findByDocumentIdOrderByIdAsc(Long documentId);

    @Query("select m from Movement m where m.lotId = :lotId order by m.id asc")
    List<Movement> findTraceByLot(Long lotId);

    /**
     * The value a sourced document moved, per item. Read from the ledger the posting
     * itself wrote, so the cost entry in the general ledger reconciles with inventory
     * by construction rather than by re-computation.
     */
    @Query("""
            select m.itemId, sum(m.amount)
            from Movement m
            where m.refType = :refType and m.refId = :refId and m.movementType = :type
            group by m.itemId
            """)
    List<Object[]> amountsByItem(String refType, String refId, MovementType type);
}
