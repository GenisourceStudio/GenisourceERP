package com.genisource.erp.purchase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/** Purchase orders: the promise to buy. */
@RestController
@RequestMapping("/api/purchase/orders")
@Tag(name = "Purchase :: orders", description = "Purchase orders")
public class PurchaseOrderController {

    private final PurchaseService purchase;

    public PurchaseOrderController(PurchaseService purchase) {
        this.purchase = purchase;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchase:order:read')")
    public com.genisource.erp.platform.common.PageResponse<PurchaseDtos.DocumentView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) com.genisource.erp.platform.common.DocumentLifecycle status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return purchase.search(PurchaseDocType.ORDER, keyword, partnerId, status, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:order:read')")
    public PurchaseDtos.DocumentView get(@PathVariable Long id) {
        return purchase.get(id, PurchaseDocType.ORDER);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('purchase:order:write')")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseDtos.DocumentView create(@Valid @RequestBody PurchaseDtos.DocumentRequest request) {
        return purchase.create(PurchaseDocType.ORDER, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:order:write')")
    public PurchaseDtos.DocumentView update(@PathVariable Long id,
                                            @Valid @RequestBody PurchaseDtos.DocumentRequest request) {
        return purchase.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:order:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        purchase.delete(id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('purchase:order:write')")
    @Operation(summary = "Submit for approval; below the threshold it is approved at once")
    public PurchaseDtos.DocumentView submit(@PathVariable Long id) {
        return purchase.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('purchase:order:write')")
    public PurchaseDtos.DocumentView approve(@PathVariable Long id,
                                             @Valid @RequestBody(required = false)
                                             PurchaseDtos.ApprovalRequest request) {
        return purchase.approve(id, request == null ? null : request.remark());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('purchase:order:write')")
    public PurchaseDtos.DocumentView reject(@PathVariable Long id,
                                            @Valid @RequestBody(required = false)
                                            PurchaseDtos.ApprovalRequest request) {
        return purchase.reject(id, request == null ? null : request.remark());
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('purchase:receipt:write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record goods arriving, as a draft receipt to be posted")
    public PurchaseDtos.DocumentView receive(@PathVariable Long id,
                                             @Valid @RequestBody PurchaseDtos.ReceiveRequest request) {
        return purchase.receive(id, request);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('purchase:order:write')")
    public PurchaseDtos.DocumentView close(@PathVariable Long id) {
        return purchase.close(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('purchase:order:write')")
    public PurchaseDtos.DocumentView cancel(@PathVariable Long id) {
        return purchase.cancel(id);
    }
}
