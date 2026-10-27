package com.genisource.erp.masterdata.bom;

import com.genisource.erp.platform.common.RevisionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BomRepository extends JpaRepository<Bom, Long>, JpaSpecificationExecutor<Bom> {

    /** Loads the component list in one query so DTO mapping does not trigger N+1. */
    @EntityGraph(attributePaths = "lines")
    Optional<Bom> findWithLinesById(Long id);

    Optional<Bom> findByItemIdAndVersion(Long itemId, String version);

    boolean existsByItemIdAndVersion(Long itemId, String version);

    List<Bom> findByItemIdOrderByVersionAsc(Long itemId);

    List<Bom> findByItemIdAndStatus(Long itemId, RevisionStatus status);

    /**
     * The revision MRP expands for an item, with its lines already loaded.
     *
     * <p>Ordering by version keeps the choice deterministic even if bad data ever leaves
     * two released revisions behind; the service prevents that, this just refuses to be
     * non-deterministic.
     */
    @EntityGraph(attributePaths = "lines")
    Optional<Bom> findFirstByItemIdAndStatusOrderByVersionDesc(Long itemId, RevisionStatus status);

    Page<Bom> findByStatus(RevisionStatus status, Pageable pageable);

    Page<Bom> findByItemId(Long itemId, Pageable pageable);

    long countByItemId(Long itemId);

    /**
     * Component edges of one item, restricted to a revision status.
     *
     * <p>Cycle detection only considers {@code RELEASED} revisions plus the draft being
     * edited: those are the edges MRP can actually expand, so a cycle hidden inside an
     * obsolete revision must not block legitimate work.
     */
    @Query("""
            select l.componentItemId from BomLine l
            where l.bom.itemId = :itemId
              and l.bom.status = :status
            """)
    List<Long> findComponentItemIds(@Param("itemId") Long itemId,
                                    @Param("status") RevisionStatus status);

    /** Items that use this item as a component; used to protect deactivation. */
    @Query("""
            select distinct l.bom.itemId from BomLine l
            where l.componentItemId = :itemId
              and l.bom.status = :status
            """)
    List<Long> findParentItemIdsUsingComponent(@Param("itemId") Long itemId,
                                               @Param("status") RevisionStatus status);

    /**
     * Items whose released BOM lists this item as an approved substitute.
     *
     * <p>Deactivating such an item would leave a live fallback pointing at something
     * nobody can buy, so it is protected exactly like a primary component.
     */
    @Query("""
            select distinct l.bom.itemId from BomSubstitute s
            join s.line l
            where s.substituteItemId = :itemId
              and l.bom.status = :status
            """)
    List<Long> findParentItemIdsUsingSubstitute(@Param("itemId") Long itemId,
                                                @Param("status") RevisionStatus status);

    /** Line counts for a page of BOMs, so the list does not fire one query per row. */
    @Query("""
            select l.bom.id, count(l) from BomLine l
            where l.bom.id in :ids
            group by l.bom.id
            """)
    List<Object[]> countLinesByBomIds(@Param("ids") java.util.Collection<Long> ids);
}
