package com.genisource.erp.production;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long>,
        JpaSpecificationExecutor<ProductionOrder> {

    Optional<ProductionOrder> findByDocNo(String docNo);

    /**
     * One order with its components loaded.
     *
     * <p>Components only. Fetching both collections in one query makes Hibernate throw
     * {@code MultipleBagFetchException} — two {@code List} associations cannot be joined
     * at once — so the operations are loaded separately by
     * {@link ProductionOperationRepository#findByOrder_IdIn}.
     */
    @Query("select distinct o from ProductionOrder o left join fetch o.lines where o.id = :id")
    Optional<ProductionOrder> findWithLinesById(Long id);

    List<ProductionOrder> findByStatusInOrderByIdAsc(Collection<ProductionOrderStatus> statuses);

    boolean existsByDocNo(String docNo);
}
