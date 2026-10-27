package com.genisource.erp.platform.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemTenantRepository extends JpaRepository<SystemTenant, Long> {

    Optional<SystemTenant> findByCode(String code);

    boolean existsByCode(String code);
}
