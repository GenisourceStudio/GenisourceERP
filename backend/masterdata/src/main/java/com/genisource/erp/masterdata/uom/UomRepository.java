package com.genisource.erp.masterdata.uom;

import com.genisource.erp.platform.common.ApiException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UomRepository extends JpaRepository<Uom, Long> {

    Optional<Uom> findByCode(String code);

    List<Uom> findAllByOrderByCodeAsc();

    /** Resolves a unit or fails with a message naming the offending code. */
    default Uom require(String code) {
        return findByCode(code)
                .orElseThrow(() -> ApiException.badRequest("Unknown unit of measure: " + code));
    }

    default boolean existsByCode(String code) {
        return findByCode(code).isPresent();
    }
}
