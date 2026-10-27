package com.genisource.erp.inventory.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface StockRepository extends JpaRepository<Stock, Long>, JpaSpecificationExecutor<Stock> {

    /** Items that still hold something — the check behind "cannot deactivate a stocked item". */
    List<Stock> findByItemIdAndOnHandGreaterThan(Long itemId, java.math.BigDecimal zero);

    long countByItemIdAndOnHandGreaterThan(Long itemId, java.math.BigDecimal zero);

    long countByWarehouseIdAndOnHandGreaterThan(Long warehouseId, java.math.BigDecimal zero);

    List<Stock> findByLotIdAndOnHandGreaterThan(Long lotId, java.math.BigDecimal zero);

    long countByLotIdAndOnHandGreaterThan(Long lotId, java.math.BigDecimal zero);
}
