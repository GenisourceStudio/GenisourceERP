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

/**
 * Purchase requisitions: the floor asking for material.
 *
 * <p>A requisition carries a quantity and a date, not a supplier or a price — those are
 * what purchasing adds when it turns the request into an order, which is exactly why
 * the two are separate documents.
 */
@RestController
@RequestMapping("/api/purchase/requisitions")
@Tag(name = "Purchase :: requisitions", description = "Purchase requisitions")
public class RequisitionController {

    private final PurchaseService purchase;

    public RequisitionController(PurchaseService purchase) {
        this.purchase = purchase;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchase:requisition:read')")
    public com.genisource.erp.platform.common.PageResponse<PurchaseDtos.DocumentView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) com.genisource.erp.platform.common.DocumentLifecycle status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return purchase.search(PurchaseDocType.REQUISITION, keyword, null, status, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:requisition:read')")
    public PurchaseDtos.DocumentView get(@PathVariable Long id) {
        return purchase.get(id, PurchaseDocType.REQUISITION);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('purchase:requisition:write')")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseDtos.DocumentView create(@Valid @RequestBody PurchaseDtos.DocumentRequest request) {
        return purchase.create(PurchaseDocType.REQUISITION, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:requisition:write')")
    public PurchaseDtos.DocumentView update(@PathVariable Long id,
                                            @Valid @RequestBody PurchaseDtos.DocumentRequest request) {
        return purchase.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:requisition:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        purchase.delete(id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('purchase:requisition:write')")
    @Operation(summary = "Submit for approval; below the threshold it is approved at once")
    public PurchaseDtos.DocumentView submit(@PathVariable Long id) {
        return purchase.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('purchase:requisition:write')")
    public PurchaseDtos.DocumentView approve(@PathVariable Long id,
                                             @Valid @RequestBody(required = false)
                                             PurchaseDtos.ApprovalRequest request) {
        return purchase.approve(id, request == null ? null : request.remark());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('purchase:requisition:write')")
    public PurchaseDtos.DocumentView reject(@PathVariable Long id,
                                            @Valid @RequestBody(required = false)
                                            PurchaseDtos.ApprovalRequest request) {
        return purchase.reject(id, request == null ? null : request.remark());
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAuthority('purchase:requisition:write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Turn an approved requisition into a purchase order")
    public PurchaseDtos.DocumentView convert(@PathVariable Long id,
                                             @Valid @RequestBody(required = false)
                                             PurchaseDtos.ConvertRequest request) {
        return purchase.convertToOrder(id, request == null
                ? new PurchaseDtos.ConvertRequest(null, null, null, null) : request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('purchase:requisition:write')")
    public PurchaseDtos.DocumentView cancel(@PathVariable Long id) {
        return purchase.cancel(id);
    }
}
