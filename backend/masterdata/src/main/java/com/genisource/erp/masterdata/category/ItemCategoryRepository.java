package com.genisource.erp.masterdata.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemCategoryRepository extends JpaRepository<ItemCategory, Long> {

    Optional<ItemCategory> findByCode(String code);

    List<ItemCategory> findAllByOrderBySortOrderAscCodeAsc();

    boolean existsByCode(String code);
}
