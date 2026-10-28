package com.genisource.erp.integration.mes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MesCompletionRepository extends JpaRepository<MesCompletion, Long> {

    Optional<MesCompletion> findByMesCompletionId(String mesCompletionId);
}
