package com.genisource.erp.platform.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Tenant filtering is applied by Hibernate, so {@code findByUsername} never sees
 * another factory's accounts even though usernames are only unique per tenant.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    boolean existsByUsername(String username);
}
