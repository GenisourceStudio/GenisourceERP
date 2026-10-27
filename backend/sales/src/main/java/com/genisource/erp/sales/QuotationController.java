package com.genisource.erp.sales;

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
 * Sales quotations: the floor asking for material.
 *
 * <p>A quotation carries a quantity and a date, not a customer or a price — those are
 * what purchasing adds when it turns the request into an order, which is exactly why
 * the two are separate documents.
 */
@RestController
@RequestMapping("/api/sales/quotations")
@Tag(name = "Sales :: quotations", description = "Sales quotations")
public class QuotationController {

    private final SalesService sales;

    public QuotationController(SalesService sales) {
        this.sales = sales;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales:quotation:read')")
    public com.genisource.erp.platform.common.PageResponse<SalesDtos.DocumentView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) com.genisource.erp.platform.common.DocumentLifecycle status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sales.search(SalesDocType.QUOTATION, keyword, null, status, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales:quotation:read')")
    public SalesDtos.DocumentView get(@PathVariable Long id) {
        return sales.get(id, SalesDocType.QUOTATION);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sales:quotation:write')")
    @ResponseStatus(HttpStatus.CREATED)
    public SalesDtos.DocumentView create(@Valid @RequestBody SalesDtos.DocumentRequest request) {
        return sales.create(SalesDocType.QUOTATION, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sales:quotation:write')")
    public SalesDtos.DocumentView update(@PathVariable Long id,
                                            @Valid @RequestBody SalesDtos.DocumentRequest request) {
        return sales.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sales:quotation:write')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        sales.delete(id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('sales:quotation:write')")
    @Operation(summary = "Submit for approval; below the threshold it is approved at once")
    public SalesDtos.DocumentView submit(@PathVariable Long id) {
        return sales.submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('sales:quotation:write')")
    public SalesDtos.DocumentView approve(@PathVariable Long id,
                                             @Valid @RequestBody(required = false)
                                             SalesDtos.ApprovalRequest request) {
        return sales.approve(id, request == null ? null : request.remark());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('sales:quotation:write')")
    public SalesDtos.DocumentView reject(@PathVariable Long id,
                                            @Valid @RequestBody(required = false)
                                            SalesDtos.ApprovalRequest request) {
        return sales.reject(id, request == null ? null : request.remark());
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAuthority('sales:quotation:write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Turn an approved quotation into a sales order")
    public SalesDtos.DocumentView convert(@PathVariable Long id,
                                             @Valid @RequestBody(required = false)
                                             SalesDtos.ConvertRequest request) {
        return sales.convertToOrder(id, request == null
                ? new SalesDtos.ConvertRequest(null, null, null, null) : request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('sales:quotation:write')")
    public SalesDtos.DocumentView cancel(@PathVariable Long id) {
        return sales.cancel(id);
    }
}
