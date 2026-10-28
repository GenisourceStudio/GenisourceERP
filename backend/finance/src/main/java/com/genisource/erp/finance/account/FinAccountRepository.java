package com.genisource.erp.finance.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface FinAccountRepository extends JpaRepository<FinAccount, Long>,
        JpaSpecificationExecutor<FinAccount> {

    List<FinAccount> findAllByOrderByCodeAsc();

    Optional<FinAccount> findByCode(String code);

    Optional<FinAccount> findBySystemRole(AccountRole systemRole);

    List<FinAccount> findByParentIdOrderByCodeAsc(Long parentId);

    boolean existsByParentId(Long parentId);
}
