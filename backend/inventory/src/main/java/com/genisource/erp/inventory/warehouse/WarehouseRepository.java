package com.genisource.erp.inventory.warehouse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByCode(String code);

    boolean existsByCode(String code);

    List<Warehouse> findAllByOrderByCodeAsc();

    /** Warehouse ids whose code or name matches a term; feeds the stock screen's search box. */
    @Query("select w.id from Warehouse w where lower(w.code) like :term or lower(w.name) like :term")
    List<Long> searchIds(@Param("term") String term);
}
