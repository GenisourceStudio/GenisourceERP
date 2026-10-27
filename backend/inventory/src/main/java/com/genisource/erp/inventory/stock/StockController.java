package com.genisource.erp.inventory.stock;

import com.genisource.erp.platform.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The live stock balance.
 *
 * <p>Read-only: stock changes when a document is posted, never through this endpoint.
 * A direct "set the quantity to 42" call would leave the ledger disagreeing with the
 * balance, and the ledger is the thing an auditor trusts.
 */
@RestController
@RequestMapping("/api/inventory/stock")
@Tag(name = "Inventory :: stock", description = "Live stock balances")
public class StockController {

    private final StockService stock;

    public StockController(StockService stock) {
        this.stock = stock;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    @Operation(summary = "Search stock balances; the keyword spans item codes, batch numbers and warehouse codes")
    public PageResponse<StockDtos.StockView> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) Long lotId,
            @RequestParam(defaultValue = "false") boolean includeEmpty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return stock.search(q, warehouseId, locationId, itemId, lotId, includeEmpty, page, size);
    }

    @GetMapping("/items/{itemId}")
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    @Operation(summary = "Stock of one item across every place it sits")
    public StockDtos.StockSummary summary(@PathVariable Long itemId) {
        return stock.summary(itemId);
    }
}
