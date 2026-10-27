package com.genisource.erp.mrp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MrpDemandRepository extends JpaRepository<MrpDemand, Long> {

    List<MrpDemand> findByRunIdOrderByItemLevelAscItemIdAsc(Long runId);

    List<MrpDemand> findByRunIdAndShortageTrueOrderByItemLevelAscItemIdAsc(Long runId);
}
