package com.genisource.erp.inventory.warehouse;

import com.genisource.erp.inventory.stock.StockRepository;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.Codes;
import com.genisource.erp.platform.common.RecordStatus;
import com.genisource.erp.platform.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class WarehouseService {

    private final WarehouseRepository warehouses;
    private final LocationRepository locations;
    private final StockRepository stock;
    private final AuditService audit;

    public WarehouseService(WarehouseRepository warehouses,
                            LocationRepository locations,
                            StockRepository stock,
                            AuditService audit) {
        this.warehouses = warehouses;
        this.locations = locations;
        this.stock = stock;
        this.audit = audit;
    }

    public List<WarehouseDtos.WarehouseView> list() {
        List<Warehouse> all = warehouses.findAllByOrderByCodeAsc();
        Map<Long, List<Location>> locationsByWarehouse = new LinkedHashMap<>();
        for (Location location : locations.findAllByOrderByWarehouseIdAscCodeAsc()) {
            locationsByWarehouse
                    .computeIfAbsent(location.getWarehouseId(), key -> new java.util.ArrayList<>())
                    .add(location);
        }
        Map<Long, Long> itemCounts = new LinkedHashMap<>();
        for (Warehouse warehouse : all) {
            long count = stock.countByWarehouseIdAndOnHandGreaterThan(
                    warehouse.getId(), BigDecimal.ZERO);
            itemCounts.put(warehouse.getId(), count);
        }
        return all.stream()
                .map(warehouse -> view(warehouse,
                        locationsByWarehouse.getOrDefault(warehouse.getId(), List.of()),
                        itemCounts.getOrDefault(warehouse.getId(), 0L)))
                .toList();
    }

    public WarehouseDtos.WarehouseView get(Long id) {
        Warehouse warehouse = require(id);
        return view(warehouse,
                locations.findByWarehouseIdOrderByCodeAsc(id),
                stock.countByWarehouseIdAndOnHandGreaterThan(id, BigDecimal.ZERO));
    }

    @Transactional
    public WarehouseDtos.WarehouseView create(WarehouseDtos.WarehouseRequest request) {
        String code = requireCode(request.code());
        if (warehouses.existsByCode(code)) {
            throw ApiException.duplicateCode("Warehouse code " + code + " already exists");
        }
        Warehouse warehouse = new Warehouse(code, request.name().trim(), request.warehouseType());
        apply(warehouse, request);
        warehouses.save(warehouse);
        audit.record("inventory.warehouse.create", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null),
                "inv_warehouse", String.valueOf(warehouse.getId()),
                Map.of("code", warehouse.getCode(), "name", warehouse.getName()));
        return get(warehouse.getId());
    }

    @Transactional
    public WarehouseDtos.WarehouseView update(Long id, WarehouseDtos.WarehouseRequest request) {
        Warehouse warehouse = require(id);
        String code = requireCode(request.code());
        if (!code.equals(warehouse.getCode()) && warehouses.existsByCode(code)) {
            throw ApiException.duplicateCode("Warehouse code " + code + " already exists");
        }
        warehouse.setCode(code);
        warehouse.setName(request.name().trim());
        warehouse.setWarehouseType(request.warehouseType());
        apply(warehouse, request);
        warehouses.save(warehouse);
        audit.record("inventory.warehouse.update", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null),
                "inv_warehouse", String.valueOf(warehouse.getId()),
                Map.of("code", warehouse.getCode(), "name", warehouse.getName()));
        return get(id);
    }

    /**
     * A warehouse that still holds stock cannot be switched off: the balances would
     * point at a place nobody may post to, and the next receipt would land in a
     * warehouse that looks closed.
     */
    @Transactional
    public WarehouseDtos.WarehouseView changeStatus(Long id, RecordStatus target) {
        Warehouse warehouse = require(id);
        if (target == RecordStatus.INACTIVE && warehouse.isActive()) {
            long held = stock.countByWarehouseIdAndOnHandGreaterThan(id, BigDecimal.ZERO);
            if (held > 0) {
                throw ApiException.inUse(
                        "Warehouse " + warehouse.getCode() + " still holds stock for "
                                + held + " item(s) and cannot be deactivated");
            }
        }
        warehouse.setStatus(target);
        warehouses.save(warehouse);
        audit.record("inventory.warehouse.status", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null),
                "inv_warehouse", String.valueOf(warehouse.getId()),
                Map.of("status", target.name()));
        return get(id);
    }

    public Warehouse require(Long id) {
        return warehouses.findById(id)
                .orElseThrow(() -> ApiException.notFound("Warehouse " + id + " does not exist"));
    }

    /** A warehouse that accepts postings: it has to exist and be switched on. */
    public Warehouse requireUsable(Long id) {
        Warehouse warehouse = require(id);
        if (!warehouse.isActive()) {
            throw ApiException.badRequest("Warehouse " + warehouse.getCode() + " is inactive");
        }
        return warehouse;
    }

    private void apply(Warehouse warehouse, WarehouseDtos.WarehouseRequest request) {
        warehouse.setAddress(blankToNull(request.address()));
        warehouse.setRemark(blankToNull(request.remark()));
        if (request.status() != null) {
            warehouse.setStatus(request.status());
        }
    }

    private WarehouseDtos.WarehouseView view(Warehouse warehouse,
                                             List<Location> locations,
                                             long stockItemCount) {
        return new WarehouseDtos.WarehouseView(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getWarehouseType(),
                warehouse.getAddress(),
                warehouse.getRemark(),
                warehouse.getStatus(),
                warehouse.getRowVersion(),
                locations.size(),
                stockItemCount,
                locations.stream()
                        .map(location -> new WarehouseDtos.LocationView(
                                location.getId(),
                                location.getWarehouseId(),
                                warehouse.getName(),
                                location.getCode(),
                                location.getName(),
                                location.getRemark(),
                                location.getStatus(),
                                location.getRowVersion()))
                        .toList());
    }

    private String requireCode(String raw) {
        String code = Codes.normalise(raw);
        if (code == null) {
            throw ApiException.badRequest("Warehouse code is required");
        }
        if (code.length() > 32) {
            throw ApiException.badRequest("Warehouse code must be at most 32 characters");
        }
        return code;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
