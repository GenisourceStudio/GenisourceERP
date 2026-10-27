package com.genisource.erp.mrp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MrpRunRepository extends JpaRepository<MrpRun, Long> {

    /** Newest first: a planner almost always wants the run they just did. */
    List<MrpRun> findAllByOrderByIdDesc();
}
