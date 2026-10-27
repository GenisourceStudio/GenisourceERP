package com.genisource.erp.masterdata.workcenter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkCenterRepository extends JpaRepository<WorkCenter, Long> {

    Optional<WorkCenter> findByCode(String code);

    boolean existsByCode(String code);

    List<WorkCenter> findAllByOrderByCodeAsc();
}
