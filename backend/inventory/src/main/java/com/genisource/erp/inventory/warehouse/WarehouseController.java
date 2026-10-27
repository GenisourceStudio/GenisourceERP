package com.genisource.erp.inventory.warehouse;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Warehouses and their bins.
 *
 * <p>Both live in one controller because they are one screen in the console: a bin
 * cannot be set up without its warehouse.
 */
@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory :: warehouses", description = "Warehouses and locations")
public class WarehouseController {

    private final WarehouseService warehouses;
    private final LocationService locations;

    public WarehouseController(WarehouseService warehouses, LocationService locations) {
        this.warehouses = warehouses;
        this.locations = locations;
    }

    @GetMapping("/warehouses")
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    public List<WarehouseDtos.WarehouseView> list() {
        return warehouses.list();
    }

    @GetMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    public WarehouseDtos.WarehouseView get(@PathVariable Long id) {
        return warehouses.get(id);
    }

    @PostMapping("/warehouses")
    @PreAuthorize("hasAuthority('inventory:stock:write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a warehouse; the code is upper-cased and must be unique")
    public WarehouseDtos.WarehouseView create(@Valid @RequestBody WarehouseDtos.WarehouseRequest request) {
        return warehouses.create(request);
    }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('inventory:stock:write')")
    public WarehouseDtos.WarehouseView update(@PathVariable Long id,
                                              @Valid @RequestBody WarehouseDtos.WarehouseRequest request) {
        return warehouses.update(id, request);
    }

    @PostMapping("/warehouses/{id}/status")
    @PreAuthorize("hasAuthority('inventory:stock:write')")
    @Operation(summary = "Activate or deactivate; deactivation is refused while the warehouse holds stock")
    public WarehouseDtos.WarehouseView changeStatus(@PathVariable Long id,
                                                     @Valid @RequestBody StatusRequest request) {
        return warehouses.changeStatus(id, request.status());
    }

    @GetMapping("/locations")
    @PreAuthorize("hasAuthority('inventory:stock:read')")
    public List<WarehouseDtos.LocationView> listLocations(
            @RequestParam(required = false) Long warehouseId) {
        return locations.list(warehouseId);
    }

    @PostMapping("/locations")
    @PreAuthorize("hasAuthority('inventory:stock:write')")
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseDtos.LocationView createLocation(
            @Valid @RequestBody WarehouseDtos.LocationRequest request) {
        return locations.create(request);
    }

    @PutMapping("/locations/{id}")
    @PreAuthorize("hasAuthority('inventory:stock:write')")
    public WarehouseDtos.LocationView updateLocation(@PathVariable Long id,
                                                    @Valid @RequestBody WarehouseDtos.LocationRequest request) {
        return locations.update(id, request);
    }

    @PostMapping("/locations/{id}/status")
    @PreAuthorize("hasAuthority('inventory:stock:write')")
    public WarehouseDtos.LocationView changeLocationStatus(@PathVariable Long id,
                                                           @Valid @RequestBody StatusRequest request) {
        return locations.changeStatus(id, request.status());
    }
}
