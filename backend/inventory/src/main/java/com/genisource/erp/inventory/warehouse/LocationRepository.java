package com.genisource.erp.inventory.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByWarehouseIdAndCode(Long warehouseId, String code);

    boolean existsByWarehouseIdAndCode(Long warehouseId, String code);

    List<Location> findByWarehouseIdOrderByCodeAsc(Long warehouseId);

    List<Location> findAllByOrderByWarehouseIdAscCodeAsc();

    long countByWarehouseId(Long warehouseId);

    /** Bins that still hold stock — used to refuse deactivating a warehouse wholesale. */
    long countByWarehouseIdAndStatus(Long warehouseId, com.genisource.erp.platform.common.RecordStatus status);
}
