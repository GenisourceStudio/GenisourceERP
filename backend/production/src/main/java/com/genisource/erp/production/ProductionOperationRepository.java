package com.genisource.erp.production;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductionOperationRepository
        extends JpaRepository<ProductionOrderOperation, Long> {

    /**
     * Operations for a whole page of orders in one query.
     *
     * <p>Loaded apart from the components because Hibernate refuses to join two bag
     * associations in the same statement. Doing it per order would be one query per row
     * on a list screen, which is the classic way a fifty-row page becomes fifty-one
     * round trips.
     */
    List<ProductionOrderOperation> findByOrder_IdIn(Collection<Long> orderIds);
}
