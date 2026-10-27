package com.genisource.erp.masterdata.item;

import com.genisource.erp.masterdata.shared.RecordStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long>, JpaSpecificationExecutor<Item> {

    Optional<Item> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCategoryId(Long categoryId);

    long countByCategoryId(Long categoryId);

    /**
     * Item count per category in one query.
     *
     * <p>The category list shows a count badge per row; issuing one count per row would
     * turn a 40-row page into 41 queries.
     */
    @Query("""
            select i.categoryId, count(i)
            from Item i
            where i.categoryId is not null
            group by i.categoryId
            """)
    List<Object[]> countGroupedByCategory();

    List<Item> findByIdIn(Collection<Long> ids);

    /**
     * Picker source for comboboxes.
     *
     * <p>Deliberately requires a search term and a page size: a dropdown that loads
     * 20 000 materials is a design smell, and a stray empty query should stay cheap.
     */
    @Query("""
            select i from Item i
            where i.status = :status
              and (lower(i.code) like :term or lower(i.name) like :term)
            order by i.code asc
            """)
    List<Item> searchTop(@Param("term") String term,
                         @Param("status") RecordStatus status,
                         Pageable pageable);
}
