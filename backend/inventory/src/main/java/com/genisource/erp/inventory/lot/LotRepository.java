package com.genisource.erp.inventory.lot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, Long> {

    Optional<Lot> findByItemIdAndLotNo(Long itemId, String lotNo);

    boolean existsByItemIdAndLotNo(Long itemId, String lotNo);

    List<Lot> findByItemIdOrderByLotNoAsc(Long itemId);

    List<Lot> findByItemIdAndLotTypeOrderByLotNoAsc(Long itemId, LotType lotType);

    /** Lot ids whose number matches a term; feeds the stock screen's search box. */
    @Query("select l.id from Lot l where lower(l.lotNo) like :term")
    List<Long> searchIds(@Param("term") String term);
}
