package com.genisource.erp.sales;

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
 * Shipments: what actually left.
 *
 * <p>Posting one is the moment stock moves and the receivable appears, which is why a
 * shipment cannot be edited or deleted afterwards — only reversed, with the correction
 * sitting next to it.
 */
@RestController
@RequestMapping("/api/sales/shipments")
@Tag(name = "Sales :: shipments", description = "Sales shipments")
public class ShipmentController {

    private final SalesService sales;

    public ShipmentController(SalesService sales) {
        this.sales = sales;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales:shipment:read')")
    public com.genisource.erp.platform.common.PageResponse<SalesDtos.DocumentView> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) com.genisource.erp.platform.common.DocumentLifecycle status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sales.search(SalesDocType.SHIPMENT, keyword, partnerId, status, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales:shipment:read')")
    public SalesDtos.DocumentView get(@PathVariable Long id) {
        return sales.get(id, SalesDocType.SHIPMENT);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sales:shipment:write')")
    @ResponseStatus(HttpStatus.CREATED)
    public SalesDtos.DocumentView create(@Valid @RequestBody SalesDtos.DocumentRequest request) {
        return sales.create(SalesDocType.SHIPMENT, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sales:shipment:write')")
    public SalesDtos.DocumentView update(@PathVariable Long id,
                                            @Valid @RequestBody SalesDtos.DocumentRequest request) {
        return sales.update(id, request);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('sales:shipment:write')")
    @Operation(summary = "Post it: the promise is released, stock leaves, the receivable is raised")
    public SalesDtos.DocumentView post(@PathVariable Long id) {
        return sales.post(id);
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('sales:shipment:write')")
    @Operation(summary = "Write the red-ink reversal; the original stays on the books")
    public SalesDtos.DocumentView reverse(@PathVariable Long id) {
        return sales.reverse(id);
    }
}
