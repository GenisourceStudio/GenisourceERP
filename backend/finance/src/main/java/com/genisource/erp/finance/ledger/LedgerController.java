package com.genisource.erp.finance.ledger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Receivables and payables.
 *
 * <p>Both sides are read-only as far as this controller is concerned: nothing here
 * creates a row, because a row is the consequence of posting a trade document, not
 * something anybody types. What can be done from here is settle money against one and
 * look at the totals.
 */
@RestController
@RequestMapping("/api/finance")
@Tag(name = "Finance :: ledger", description = "Receivables and payables")
public class LedgerController {

    private final ReceivableService receivables;
    private final PayableService payables;

    public LedgerController(ReceivableService receivables, PayableService payables) {
        this.receivables = receivables;
        this.payables = payables;
    }

    @GetMapping("/receivables")
    @PreAuthorize("hasAuthority('finance:receivable:read')")
    public LedgerDtos.LedgerPage listReceivables(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) LedgerStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean overdueOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return receivables.search(keyword, partnerId, status, from, to, overdueOnly, page, size);
    }

    @GetMapping("/receivables/{id}")
    @PreAuthorize("hasAuthority('finance:receivable:read')")
    public LedgerDtos.LedgerView getReceivable(@PathVariable Long id) {
        return receivables.get(id);
    }

    @PostMapping("/receivables/{id}/payment")
    @PreAuthorize("hasAuthority('finance:receivable:write')")
    @Operation(summary = "Record money received against a receivable")
    public LedgerDtos.LedgerView collectReceivable(
            @PathVariable Long id,
            @Valid @RequestBody LedgerDtos.SettleRequest request) {
        return receivables.settle(id, request);
    }

    @GetMapping("/payables")
    @PreAuthorize("hasAuthority('finance:payable:read')")
    public LedgerDtos.LedgerPage listPayables(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) LedgerStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean overdueOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return payables.search(keyword, partnerId, status, from, to, overdueOnly, page, size);
    }

    @GetMapping("/payables/{id}")
    @PreAuthorize("hasAuthority('finance:payable:read')")
    public LedgerDtos.LedgerView getPayable(@PathVariable Long id) {
        return payables.get(id);
    }

    @PostMapping("/payables/{id}/payment")
    @PreAuthorize("hasAuthority('finance:payable:write')")
    @Operation(summary = "Record money paid against a payable")
    public LedgerDtos.LedgerView payPayable(@PathVariable Long id,
                                            @Valid @RequestBody LedgerDtos.SettleRequest request) {
        return payables.settle(id, request);
    }
}
