package com.genisource.erp.platform.security;

import com.genisource.erp.platform.tenant.TenantContext;
import com.genisource.erp.platform.tenant.TenantRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator on an empty database.
 *
 * <p>Runs before {@code AdminBootstrap} of other modules ({@code @Order(10)}) so that
 * later initialisers can rely on at least one account existing. Idempotent: if the
 * account is already present nothing is touched, which keeps restarts cheap and makes
 * the password in {@code .env} authoritative only on the very first boot.
 */
@Component
@Order(10)
public class InitialAdminCreator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminCreator.class);

    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final TenantRegistry tenantRegistry;
    private final SecurityProperties properties;
    private final PasswordEncoder passwordEncoder;

    public InitialAdminCreator(UserAccountRepository users,
                               RoleRepository roles,
                               TenantRegistry tenantRegistry,
                               SecurityProperties properties,
                               PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.tenantRegistry = tenantRegistry;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        long tenantId = tenantRegistry.defaultTenantId();
        TenantContext.callWith(tenantId, () -> {
            String username = properties.adminUsername();
            if (users.existsByUsername(username)) {
                return null;
            }

            Role adminRole = roles.findByCode("ADMIN")
                    .orElseThrow(() -> new IllegalStateException(
                            "Built-in role ADMIN is missing. Flyway V2 seeds it."));

            UserAccount admin = new UserAccount(
                    username,
                    passwordEncoder.encode(properties.adminPassword()),
                    "系统管理员");
            admin.addRole(adminRole);
            admin.setForcePasswordChange(properties.adminForcePasswordChange());
            users.save(admin);

            log.info("Created initial administrator '{}' for tenant {} (force password change: {})",
                    username, tenantId, properties.adminForcePasswordChange());
            return null;
        });
    }
}
