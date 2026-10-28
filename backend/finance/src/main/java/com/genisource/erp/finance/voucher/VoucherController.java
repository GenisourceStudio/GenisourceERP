package com.genisource.erp.finance.voucher;

import com.genisource.erp.platform.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Vouchers.
 *
 * <p>Writing and posting are separated because they are different jobs: a bookkeeper
 * types what happened, an accountant decides it counts. The automatic vouchers skip the
 * review step on purpose — a receipt that already moved stock must not wait for a human
 * to agree that it moved — and their type says where they came from.
 */
@RestController
@RequestMapping("/api/finance/vouchers")
@Tag(name = "Finance :: vouchers", description = "General ledger vouchers")
public class VoucherController {

    private final VoucherService vouchers;

    public VoucherController(VoucherService vouchers) {
        this.vouchers = vouchers;
    }

    @GetMapping
    @Operation(summary = "Search vouchers")
    @PreAuthorize("hasAuthority('finance:voucher:read')")
    public PageResponse<VoucherDtos.VoucherView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VoucherType voucherType,
            @RequestParam(required = false) VoucherStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return vouchers.search(keyword, voucherType, status, from, to, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One voucher with its lines")
    @PreAuthorize("hasAuthority('finance:voucher:read')")
    public VoucherDtos.VoucherView get(@PathVariable Long id) {
        return vouchers.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Write a voucher (stays draft until posted)")
    @PreAuthorize("hasAuthority('finance:voucher:write')")
    public VoucherDtos.VoucherView create(@Valid @RequestBody VoucherDtos.VoucherRequest request) {
        return vouchers.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a draft voucher")
    @PreAuthorize("hasAuthority('finance:voucher:write')")
    public VoucherDtos.VoucherView update(@PathVariable Long id,
                                          @Valid @RequestBody VoucherDtos.VoucherRequest request) {
        return vouchers.update(id, request);
    }

    @PostMapping("/{id}/post")
    @Operation(summary = "Post a draft; it counts from this moment")
    @PreAuthorize("hasAuthority('finance:voucher:post')")
    public VoucherDtos.VoucherView post(@PathVariable Long id) {
        return vouchers.post(id);
    }

    @PostMapping("/{id}/reverse")
    @Operation(summary = "Reverse a posted voucher with a red-letter one")
    @PreAuthorize("hasAuthority('finance:voucher:post')")
    public VoucherDtos.VoucherView reverse(@PathVariable Long id,
                                           @Valid @RequestBody VoucherDtos.ReverseRequest request) {
        return vouchers.reverse(id, request);
    }
}
