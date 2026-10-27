package com.genisource.erp.inventory.lot;

import com.genisource.erp.platform.common.RecordStatus;
import com.genisource.erp.platform.common.StatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/lots")
@Tag(name = "Inventory :: batches", description = "Batches and serial numbers")
public class LotController {

    private final LotService lots;

    public LotController(LotService lots) {
        this.lots = lots;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    public List<LotDtos.LotView> list(@RequestParam(required = false) Long itemId) {
        return lots.list(itemId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    public LotDtos.LotView get(@PathVariable Long id) {
        return lots.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('inventory:stock:write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a batch; the type has to agree with how the item is tracked")
    public LotDtos.LotView create(@Valid @RequestBody LotDtos.LotRequest request) {
        return lots.create(request);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('inventory:stock:write')")
    @Operation(summary = "Deactivate a batch; refused while it still holds stock")
    public LotDtos.LotView changeStatus(@PathVariable Long id,
                                         @Valid @RequestBody StatusRequest request) {
        return lots.changeStatus(id, request.status());
    }
}
