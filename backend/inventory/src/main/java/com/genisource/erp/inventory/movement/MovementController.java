package com.genisource.erp.inventory.movement;

import com.genisource.erp.platform.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/inventory/movements")
@Tag(name = "Inventory :: ledger", description = "The inventory ledger and batch traceability")
public class MovementController {

    private final MovementService movements;

    public MovementController(MovementService movements) {
        this.movements = movements;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    @Operation(summary = "Search the ledger")
    public PageResponse<MovementDtos.MovementView> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return movements.search(q, itemId, lotId, warehouseId, movementType, from, to, page, size);
    }

    @GetMapping("/lots/{lotId}/trace")
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    @Operation(summary = "Everything that ever happened to one batch")
    public MovementDtos.LotTrace traceLot(@PathVariable Long lotId) {
        return movements.traceLot(lotId);
    }
}
