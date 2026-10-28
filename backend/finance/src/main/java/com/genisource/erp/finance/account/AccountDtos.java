package com.genisource.erp.finance.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record AccountRequest(
            @NotBlank(message = "{validation.code.required}")
            @Size(max = 32, message = "{validation.code.size}")
            @Pattern(regexp = "[^\\s]+", message = "{validation.code.whitespace}")
            String code,

            @NotBlank(message = "{validation.name.required}")
            @Size(max = 128, message = "{validation.name.size}")
            String name,

            @NotNull(message = "{validation.accountType.required}")
            AccountType accountType,

            Long parentId,

            Boolean active,

            AccountRole systemRole,

            @Size(max = 255, message = "{validation.remark.size}")
            String remark) {
    }

    /** What may change after posting has begun: the label and the switches, never the identity. */
    public record AccountUpdateRequest(
            @NotBlank(message = "{validation.name.required}")
            @Size(max = 128, message = "{validation.name.size}")
            String name,

            Boolean active,

            AccountRole systemRole,

            @Size(max = 255, message = "{validation.remark.size}")
            String remark) {
    }

    public record AccountView(
            Long id,
            String code,
            String name,
            AccountType accountType,
            BalanceDirection balanceDirection,
            Long parentId,
            int level,
            boolean leaf,
            boolean active,
            AccountRole systemRole,
            /** True once a voucher line points at this account; identity then freezes. */
            boolean used,
            String remark) {
    }

    public record AccountTree(List<AccountView> accounts) {
    }
}
