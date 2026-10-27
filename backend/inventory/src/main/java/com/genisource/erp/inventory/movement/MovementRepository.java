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
}
