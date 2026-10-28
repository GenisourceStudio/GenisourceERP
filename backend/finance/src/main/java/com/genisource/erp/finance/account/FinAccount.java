package com.genisource.erp.finance.account;

import com.genisource.erp.platform.common.VersionedTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * One line of the chart of accounts.
 *
 * <p>The code is the account's identity to an accountant ("1403 原材料") and the name is
 * what appears on a report. Both are editable while the account is unused, because the
 * first week of an implementation is exactly when a chart gets rearranged; once a
 * voucher line points at an account, its code and type freeze — rewriting them would
 * silently re-label every historical report.
 *
 * <p>Parents exist so the chart reads like the printed one. Only leaves accept postings:
 * a posting to a heading is almost always a mistake, and catching it here is cheaper
 * than explaining a trial balance that no longer adds up.
 */
@Entity
@Table(name = "fin_account")
public class FinAccount extends VersionedTenantEntity {

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 16)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "balance_direction", nullable = false, length = 8)
    private BalanceDirection balanceDirection;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "level", nullable = false)
    private int level = 1;

    @Column(name = "is_leaf", nullable = false)
    private boolean leaf = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", length = 32)
    private AccountRole systemRole;

    @Column(name = "remark", length = 255)
    private String remark;

    protected FinAccount() {
    }

    public FinAccount(String code, String name, AccountType accountType, Long parentId, int level) {
        this.code = code;
        this.name = name;
        this.accountType = accountType;
        this.balanceDirection = accountType.normal();
        this.parentId = parentId;
        this.level = level;
        this.leaf = true;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void activate(boolean active) {
        this.active = active;
    }

    /** Points this account at a role, or clears the pointer when {@code role} is null. */
    public void assignRole(AccountRole role) {
        this.systemRole = role;
    }

    /**
     * Marks this account as a heading. Called when a child is added, so that a parent
     * stops accepting postings the moment it gains something below it.
     */
    public void makeParent() {
        this.leaf = false;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public BalanceDirection getBalanceDirection() {
        return balanceDirection;
    }

    public Long getParentId() {
        return parentId;
    }

    public int getLevel() {
        return level;
    }

    public boolean isLeaf() {
        return leaf;
    }

    public boolean isActive() {
        return active;
    }

    public AccountRole getSystemRole() {
        return systemRole;
    }

    public String getRemark() {
        return remark;
    }
}
