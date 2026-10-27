package com.genisource.erp.masterdata.partner;

import com.genisource.erp.masterdata.shared.RecordStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PartnerRepository extends JpaRepository<Partner, Long>, JpaSpecificationExecutor<Partner> {

    Optional<Partner> findByCode(String code);

    boolean existsByCode(String code);

    @Query("""
            select p from Partner p
            where p.status = :status
              and (p.partnerType = :type or p.partnerType = com.genisource.erp.masterdata.partner.PartnerType.BOTH)
              and (lower(p.code) like :term or lower(p.name) like :term)
            order by p.code asc
            """)
    List<Partner> searchTop(@Param("term") String term,
                            @Param("type") PartnerType type,
                            @Param("status") RecordStatus status,
                            Pageable pageable);
}
