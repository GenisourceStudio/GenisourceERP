package com.genisource.erp.finance.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The chart of accounts.
 *
 * <p>Read access is wide because every posting screen needs the account list; changing
 * the chart is narrow on purpose, since it re-labels every report the ledger ever
 * produced.
 */
@RestController
@RequestMapping("/api/finance/accounts")
@Tag(name = "Finance :: accounts", description = "Chart of accounts")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    @Operation(summary = "List the chart of accounts (seeded on first call)")
    @PreAuthorize("hasAuthority('finance:account:read')")
    public List<AccountDtos.AccountView> list() {
        return accounts.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an account")
    @PreAuthorize("hasAuthority('finance:account:write')")
    public AccountDtos.AccountView create(@Valid @RequestBody AccountDtos.AccountRequest request) {
        return accounts.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename, activate, deactivate or re-point an account")
    @PreAuthorize("hasAuthority('finance:account:write')")
    public AccountDtos.AccountView update(@PathVariable Long id,
                                          @Valid @RequestBody AccountDtos.AccountUpdateRequest request) {
        return accounts.update(id, request);
    }
}
