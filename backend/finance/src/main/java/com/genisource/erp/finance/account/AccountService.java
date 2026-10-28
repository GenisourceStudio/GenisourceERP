package com.genisource.erp.finance.account;

import com.genisource.erp.finance.voucher.FinVoucherLineRepository;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.Codes;
import com.genisource.erp.platform.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * The chart of accounts: what exists, what may be posted to, and which account plays
 * which role in an automatic posting.
 *
 * <p>The chart is seeded, not designed in a vacuum. A factory switching on M5 needs
 * 收料 to post the moment the first receipt lands, and asking the accountant to first
 * design seventeen accounts would turn that receipt into a configuration project. The
 * seed is the small-business standard chart; everything about it can be changed after.
 *
 * <p>What may not change silently is an account's identity. Once a voucher line points
 * at an account, its code and type are frozen: editing them would re-label every
 * historical report without touching a single number, which is the quietest way to
 * make last year's balance sheet lie.
 */
@Service
public class AccountService {

    private final FinAccountRepository accounts;
    private final FinVoucherLineRepository voucherLines;
    private final AccountSeeder seeder;
    private final AuditService audit;

    public AccountService(FinAccountRepository accounts,
                          FinVoucherLineRepository voucherLines,
                          AccountSeeder seeder,
                          AuditService audit) {
        this.accounts = accounts;
        this.voucherLines = voucherLines;
        this.seeder = seeder;
        this.audit = audit;
    }

    // ------------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public List<AccountDtos.AccountView> list() {
        seeder.ensureSeeded();
        return accounts.findAllByOrderByCodeAsc().stream().map(this::view).toList();
    }

    /**
     * The account that plays a role in an automatic posting, or a failure that names
     * the role rather than an id: the person fixing this needs to open the accounts
     * screen and point the role somewhere, and "account 14 not found" does not say that.
     */
    @Transactional(readOnly = true)
    public FinAccount requireRole(AccountRole role) {
        seeder.ensureSeeded();
        FinAccount account = accounts.findBySystemRole(role).orElseThrow(() ->
                ApiException.invalidState("没有科目承担 " + role + " 角色，请在科目表里指定"));
        if (!account.isActive() || !account.isLeaf()) {
            throw ApiException.invalidState(
                    "角色 " + role + " 指向的科目 " + account.getCode() + " 已停用或不是明细科目");
        }
        return account;
    }

    @Transactional(readOnly = true)
    public FinAccount require(Long id) {
        return accounts.findById(id)
                .orElseThrow(() -> ApiException.notFound("Account " + id + " does not exist"));
    }

    // ------------------------------------------------------------------ write

    @Transactional
    public AccountDtos.AccountView create(AccountDtos.AccountRequest request) {
        String code = Codes.normalise(request.code());
        if (accounts.findByCode(code).isPresent()) {
            throw ApiException.duplicateCode("科目代码 " + code + " 已存在");
        }
        FinAccount parent = null;
        int level = 1;
        if (request.parentId() != null) {
            parent = require(request.parentId());
            if (!parent.isLeaf()) {
                throw ApiException.invalidState("父科目 " + parent.getCode() + " 已经是汇总科目");
            }
            level = parent.getLevel() + 1;
            parent.makeParent();
        }
        FinAccount account = new FinAccount(code, request.name().trim(), request.accountType(),
                parent == null ? null : parent.getId(), level);
        if (request.active() != null) {
            account.activate(request.active());
        }
        if (request.remark() != null) {
            account.setRemark(request.remark().trim());
        }
        assignRoleDistinct(account, request.systemRole());
        accounts.save(account);
        audit.record("finance.account.create", CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "fin_account", String.valueOf(account.getId()),
                Map.of("code", code, "name", account.getName()));
        return view(account);
    }

    /**
     * Edits what may change. The code, type, direction and parent never do — not even
     * before first use, because a code typed wrong and corrected silently is exactly
     * the kind of "it was always 1405" disagreement a ledger exists to settle.
     */
    @Transactional
    public AccountDtos.AccountView update(Long id, AccountDtos.AccountUpdateRequest request) {
        FinAccount account = require(id);
        account.rename(request.name().trim());
        if (request.active() != null) {
            if (!request.active() && accounts.existsByParentId(id)) {
                throw ApiException.invalidState("科目 " + account.getCode() + " 有下级科目，不能停用");
            }
            account.activate(request.active());
        }
        if (request.remark() != null) {
            account.setRemark(request.remark().trim());
        }
        assignRoleDistinct(account, request.systemRole());
        audit.record("finance.account.update", CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "fin_account", String.valueOf(id), Map.of("name", account.getName()));
        return view(account);
    }

    /** One account per role: taking a role takes it away from whoever had it. */
    private void assignRoleDistinct(FinAccount account, AccountRole role) {
        if (role == null) {
            account.assignRole(null);
            return;
        }
        accounts.findBySystemRole(role)
                .filter(other -> !other.getId().equals(account.getId()))
                .ifPresent(other -> other.assignRole(null));
        account.assignRole(role);
    }

    private AccountDtos.AccountView view(FinAccount account) {
        return new AccountDtos.AccountView(
                account.getId(), account.getCode(), account.getName(),
                account.getAccountType(), account.getBalanceDirection(),
                account.getParentId(), account.getLevel(), account.isLeaf(), account.isActive(),
                account.getSystemRole(), voucherLines.existsByAccountId(account.getId()),
                account.getRemark());
    }
}
