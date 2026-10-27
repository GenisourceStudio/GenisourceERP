package com.genisource.erp.purchase;

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
 * Goods receipts: what actually arrived.
 *
 * <p>Posting one is the moment stock moves and the payable appears, which is why a
 * receipt cannot be edited or deleted afterwards — only reversed, with the correction
 * sitting next to it.
 */
@RestController
@RequestMapping("/api/purchase/receipts")
@Tag(name = "Purchase :: receipts", description = "Goods receipts")
public class GoodsReceiptController {

    private final PurchaseService purchase;

    public GoodsReceiptController(PurchaseService purchase) {
        this.purchase = purchase;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchase:receipt:read')")
    public com.genisource.erp.platform.common.PageResponse<PurchaseDtos.DocumentView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) com.genisource.erp.platform.common.DocumentLifecycle status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return purchase.search(PurchaseDocType.RECEIPT, keyword, partnerId, status, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:receipt:read')")
    public PurchaseDtos.DocumentView get(@PathVariable Long id) {
        return purchase.get(id, PurchaseDocType.RECEIPT);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('purchase:receipt:write')")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseDtos.DocumentView create(@Valid @RequestBody PurchaseDtos.DocumentRequest request) {
        return purchase.create(PurchaseDocType.RECEIPT, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:receipt:write')")
    public PurchaseDtos.DocumentView update(@PathVariable Long id,
                                            @Valid @RequestBody PurchaseDtos.DocumentRequest request) {
        return purchase.update(id, request);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('purchase:receipt:write')")
    @Operation(summary = "Post it: stock arrives, the payable is raised, the order records it")
    public PurchaseDtos.DocumentView post(@PathVariable Long id) {
        return purchase.post(id);
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('purchase:receipt:write')")
    @Operation(summary = "Write the red-ink reversal; the original stays on the books")
    public PurchaseDtos.DocumentView reverse(@PathVariable Long id) {
        return purchase.reverse(id);
    }
}
