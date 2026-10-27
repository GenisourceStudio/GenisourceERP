package com.genisource.erp.masterdata.routing;

import com.genisource.erp.masterdata.shared.RevisionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface RoutingRepository extends JpaRepository<Routing, Long>, JpaSpecificationExecutor<Routing> {

    @EntityGraph(attributePaths = "operations")
    Optional<Routing> findWithOperationsById(Long id);

    Optional<Routing> findByItemIdAndVersion(Long itemId, String version);

    boolean existsByItemIdAndVersion(Long itemId, String version);

    List<Routing> findByItemIdOrderByVersionAsc(Long itemId);

    List<Routing> findByItemIdAndStatus(Long itemId, RevisionStatus status);

    Page<Routing> findByItemId(Long itemId, Pageable pageable);

    long countByItemId(Long itemId);
}
