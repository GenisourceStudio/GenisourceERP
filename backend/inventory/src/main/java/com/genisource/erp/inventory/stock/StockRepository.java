package com.genisource.erp.inventory.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface StockRepository extends JpaRepository<Stock, Long>, JpaSpecificationExecutor<Stock> {

    /**
     * One row per item, with the four quantities already summed across warehouses.
     *
     * <p>Grouped in the database rather than in memory: a tenant with three thousand items
     * and a bin per item would otherwise load tens of thousands of rows to add up four
     * columns. The tenant predicate is added by Hibernate, so the result never spans
     * customers.
     */
    @Query("""
            select new com.genisource.erp.inventory.stock.ItemBalance(
                s.itemId,
                sum(s.onHand),
                sum(s.reserved),
                sum(s.locked),
                sum(s.inTransit))
            from Stock s
            group by s.itemId
            """)
    List<ItemBalance> balanceByItem();

    /** Items that still hold something — the check behind "cannot deactivate a stocked item". */
    List<Stock> findByItemIdAndOnHandGreaterThan(Long itemId, java.math.BigDecimal zero);

    long countByItemIdAndOnHandGreaterThan(Long itemId, java.math.BigDecimal zero);

    long countByWarehouseIdAndOnHandGreaterThan(Long warehouseId, java.math.BigDecimal zero);

    List<Stock> findByLotIdAndOnHandGreaterThan(Long lotId, java.math.BigDecimal zero);

    long countByLotIdAndOnHandGreaterThan(Long lotId, java.math.BigDecimal zero);
}
