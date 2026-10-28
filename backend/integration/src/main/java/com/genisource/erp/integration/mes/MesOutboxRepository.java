package com.genisource.erp.integration.mes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MesOutboxRepository extends JpaRepository<MesOutbox, Long> {

    Optional<MesOutbox> findByDedupeKey(String dedupeKey);

    List<MesOutbox> findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            MesOutbox.Status status, Instant moment);
}
