package com.genisource.erp.finance.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Puts the standard chart in place for a tenant that has none.
 *
 * <p>This is a separate bean for one reason: {@code @Transactional} only applies when
 * the call goes through the proxy. A seeding method called from inside the service
 * that owns it would be a self-invocation — the annotation would be ignored, the
 * write would join the caller's read-only transaction, and PostgreSQL would refuse it
 * with "cannot execute INSERT in a read-only transaction". Splitting it out means the
 * seeding always runs in its own transaction, whoever asks for it.
 */
@Service
@Order(20)
public class AccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountSeeder.class);

    private final FinAccountRepository accounts;

    public AccountSeeder(FinAccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * Seeds the standard chart for every tenant that has none. Runs after the tenant
     * registry ({@code @Order(0)}) and the initial admin ({@code @Order(10)}), so the
     * tenants it seeds for actually exist.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run(ApplicationArguments args) {
        ensureSeeded();
    }

    /**
     * Seeds the standard chart when this tenant has no accounts at all. Safe to call
     * from anywhere: it is a no-op once the chart exists, and a tenant created after
     * boot gets its chart the first time anyone opens the accounts screen or posts a
     * voucher.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureSeeded() {
        if (accounts.count() > 0) {
            return;
        }
        Map<String, FinAccount> byCode = new HashMap<>();
        for (ChartOfAccounts.Seed seed : ChartOfAccounts.SEEDS) {
            FinAccount parent = seed.parentCode() == null ? null : byCode.get(seed.parentCode());
            FinAccount account = new FinAccount(seed.code(), seed.name(), seed.type(),
                    parent == null ? null : parent.getId(), parent == null ? 1 : parent.getLevel() + 1);
            if (parent != null) {
                parent.makeParent();
            }
            if (seed.role() != null) {
                account.assignRole(seed.role());
            }
            accounts.save(account);
            byCode.put(seed.code(), account);
        }
        log.info("Seeded the standard chart of accounts ({} accounts)",
                ChartOfAccounts.SEEDS.size());
    }
}
