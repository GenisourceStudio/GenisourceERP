package com.genisource.erp.mrp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MrpPlanSupplyRepository extends JpaRepository<MrpPlanSupply, Long> {

    List<MrpPlanSupply> findByRunIdOrderBySupplyTypeAscItemIdAsc(Long runId);
}
