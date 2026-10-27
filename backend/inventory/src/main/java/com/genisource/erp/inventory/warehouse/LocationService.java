package com.genisource.erp.inventory.warehouse;

import com.genisource.erp.inventory.warehouse.Warehouse;
import com.genisource.erp.inventory.warehouse.WarehouseService;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.Codes;
import com.genisource.erp.platform.common.RecordStatus;
import com.genisource.erp.platform.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locations;
    private final WarehouseService warehouses;
    private final AuditService audit;

    public LocationService(LocationRepository locations,
                            WarehouseService warehouses,
                            AuditService audit) {
        this.locations = locations;
        this.warehouses = warehouses;
        this.audit = audit;
    }

    public List<WarehouseDtos.LocationView> list(Long warehouseId) {
        List<Location> source = warehouseId == null
                ? locations.findAllByOrderByWarehouseIdAscCodeAsc()
                : locations.findByWarehouseIdOrderByCodeAsc(warehouseId);
        Map<Long, String> warehouseNames = source.stream()
                .map(Location::getWarehouseId)
                .distinct()
                .map(warehouses::require)
                .collect(java.util.stream.Collectors.toMap(Warehouse::getId, Warehouse::getName));
        return source.stream().map(location -> view(location, warehouseNames)).toList();
    }

    @Transactional
    public WarehouseDtos.LocationView create(WarehouseDtos.LocationRequest request) {
        Warehouse warehouse = warehouses.requireUsable(request.warehouseId());
        String code = requireCode(request.code());
        if (locations.existsByWarehouseIdAndCode(warehouse.getId(), code)) {
            throw ApiException.duplicateCode(
                    "Location code " + code + " already exists in warehouse " + warehouse.getCode());
        }
        Location location = new Location(warehouse.getId(), code, request.name().trim());
        apply(location, request);
        locations.save(location);
        audit.record("inventory.location.create", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null),
                "inv_location", String.valueOf(location.getId()),
                Map.of("code", location.getCode(), "warehouse", warehouse.getCode()));
        return view(location, Map.of(warehouse.getId(), warehouse.getName()));
    }

    @Transactional
    public WarehouseDtos.LocationView update(Long id, WarehouseDtos.LocationRequest request) {
        Location location = require(id);
        Warehouse warehouse = warehouses.requireUsable(request.warehouseId());
        String code = requireCode(request.code());
        if (!location.getWarehouseId().equals(warehouse.getId())
                || !code.equals(location.getCode())) {
            if (locations.existsByWarehouseIdAndCode(warehouse.getId(), code)) {
                throw ApiException.duplicateCode("Location code " + code
                        + " already exists in warehouse " + warehouse.getCode());
            }
        }
        location.setWarehouseId(warehouse.getId());
        location.setCode(code);
        location.setName(request.name().trim());
        apply(location, request);
        locations.save(location);
        audit.record("inventory.location.update", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null),
                "inv_location", String.valueOf(location.getId()),
                Map.of("code", location.getCode()));
        return view(location, Map.of(warehouse.getId(), warehouse.getName()));
    }

    @Transactional
    public WarehouseDtos.LocationView changeStatus(Long id, RecordStatus target) {
        Location location = require(id);
        location.setStatus(target);
        locations.save(location);
        audit.record("inventory.location.status", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null),
                "inv_location", String.valueOf(location.getId()),
                Map.of("status", target.name()));
        Warehouse warehouse = warehouses.require(location.getWarehouseId());
        return view(location, Map.of(warehouse.getId(), warehouse.getName()));
    }

    public Location require(Long id) {
        return locations.findById(id)
                .orElseThrow(() -> ApiException.notFound("Location " + id + " does not exist"));
    }

    /** A location that accepts postings: it has to exist, be active and sit in the warehouse given. */
    public Location requireUsable(Long id, Long warehouseId) {
        Location location = require(id);
        if (!location.getWarehouseId().equals(warehouseId)) {
            throw ApiException.badRequest(
                    "Location " + location.getCode() + " does not belong to the selected warehouse");
        }
        if (!location.isActive()) {
            throw ApiException.badRequest("Location " + location.getCode() + " is inactive");
        }
        return location;
    }

    private void apply(Location location, WarehouseDtos.LocationRequest request) {
        location.setRemark(request.remark() == null || request.remark().isBlank()
                ? null : request.remark().trim());
        if (request.status() != null) {
            location.setStatus(request.status());
        }
    }

    private WarehouseDtos.LocationView view(Location location, Map<Long, String> warehouseNames) {
        return new WarehouseDtos.LocationView(
                location.getId(),
                location.getWarehouseId(),
                warehouseNames.get(location.getWarehouseId()),
                location.getCode(),
                location.getName(),
                location.getRemark(),
                location.getStatus(),
                location.getRowVersion());
    }

    private String requireCode(String raw) {
        String code = Codes.normalise(raw);
        if (code == null) {
            throw ApiException.badRequest("Location code is required");
        }
        if (code.length() > 32) {
            throw ApiException.badRequest("Location code must be at most 32 characters");
        }
        return code;
    }
}
