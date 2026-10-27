package com.genisource.erp.inventory.movement;

import com.genisource.erp.inventory.lot.Lot;
import com.genisource.erp.inventory.lot.LotRepository;
import com.genisource.erp.inventory.stock.StockService;
import com.genisource.erp.inventory.warehouse.Warehouse;
import com.genisource.erp.inventory.warehouse.WarehouseRepository;
import com.genisource.erp.masterdata.item.Item;
import com.genisource.erp.masterdata.item.ItemRepository;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.genisource.erp.inventory.warehouse.Location;
import com.genisource.erp.inventory.warehouse.LocationRepository;

/**
 * The inventory ledger.
 *
 * <p>Read-only by design: entries are written by posting a document and are never
 * edited afterwards. Everything a reader might want — which batch came in when, what
 * a document was worth, where the stock came from — is answered from here.
 */
@Service
@Transactional(readOnly = true)
public class MovementService {

    private final MovementRepository movements;
    private final ItemRepository items;
    private final WarehouseRepository warehouses;
    private final LocationRepository locations;
    private final LotRepository lots;
    private final StockService stock;

    public MovementService(MovementRepository movements,
                           ItemRepository items,
                           WarehouseRepository warehouses,
                           LocationRepository locations,
                           LotRepository lots,
                           StockService stock) {
        this.movements = movements;
        this.items = items;
        this.warehouses = warehouses;
        this.locations = locations;
        this.lots = lots;
        this.stock = stock;
    }

    public PageResponse<MovementDtos.MovementView> search(String keyword,
                                                          Long itemId,
                                                          Long lotId,
                                                          Long warehouseId,
                                                          MovementType movementType,
                                                          LocalDate from,
                                                          LocalDate to,
                                                          int page,
                                                          int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by("id").descending());
        Page<Movement> result = movements.findAll(
                specification(keyword, itemId, lotId, warehouseId, movementType, from, to), pageable);
        return PageResponse.of(result, views(result.getContent()));
    }

    /**
     * Everything that ever happened to one batch.
     *
     * <p>This is the answer to "where did this unit come from" — the question a
     * customer asks when a machine fails, and the question an auditor asks about a
     * recall.
     */
    public MovementDtos.LotTrace traceLot(Long lotId) {
        Lot lot = lots.findById(lotId)
                .orElseThrow(() -> ApiException.notFound("Lot " + lotId + " does not exist"));
        Item item = items.findById(lot.getItemId()).orElse(null);
        List<MovementDtos.MovementView> entries = views(movements.findTraceByLot(lotId));
        BigDecimal[] balance = stock.lotBalance(lotId);
        return new MovementDtos.LotTrace(
                lot.getId(),
                lot.getLotNo(),
                lot.getItemId(),
                item == null ? null : item.getCode(),
                item == null ? null : item.getName(),
                lot.getLotType().name(),
                balance[0],
                balance[1],
                lot.getProductionDate(),
                lot.getExpiryDate(),
                entries);
    }

    List<MovementDtos.MovementView> views(List<Movement> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        Map<Long, Item> itemById = items
                .findAllById(source.stream().map(Movement::getItemId).distinct().toList())
                .stream().collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<Long, Warehouse> warehouseById = warehouses
                .findAllById(source.stream().map(Movement::getWarehouseId).distinct().toList())
                .stream().collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        Map<Long, Location> locationById = locations
                .findAllById(source.stream().map(Movement::getLocationId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Location::getId, Function.identity()));
        Map<Long, Lot> lotById = lots
                .findAllById(source.stream().map(Movement::getLotId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Lot::getId, Function.identity()));

        return source.stream().map(movement -> {
            Item item = itemById.get(movement.getItemId());
            Warehouse warehouse = warehouseById.get(movement.getWarehouseId());
            Location location = locationById.get(movement.getLocationId());
            Lot lot = lotById.get(movement.getLotId());
            return new MovementDtos.MovementView(
                    movement.getId(),
                    movement.getRefId(),
                    movement.getMovementType(),
                    movement.getDirection(),
                    movement.getItemId(),
                    item == null ? null : item.getCode(),
                    item == null ? null : item.getName(),
                    item == null ? null : item.getUomCode(),
                    movement.getLotId(),
                    lot == null ? null : lot.getLotNo(),
                    movement.getWarehouseId(),
                    warehouse == null ? null : warehouse.getCode(),
                    warehouse == null ? null : warehouse.getName(),
                    movement.getLocationId(),
                    location == null ? null : location.getCode() + " · " + location.getName(),
                    movement.getQty(),
                    movement.getUnitCost(),
                    movement.getAmount(),
                    movement.getBalanceQtyAfter(),
                    movement.getBalanceAmountAfter(),
                    movement.getOperator(),
                    movement.getRemark(),
                    movement.getMovedAt());
        }).toList();
    }

    private Specification<Movement> specification(String keyword,
                                                   Long itemId,
                                                   Long lotId,
                                                   Long warehouseId,
                                                   MovementType movementType,
                                                   LocalDate from,
                                                   LocalDate to) {
        Specification<Movement> spec = (root, query, cb) -> cb.conjunction();
        if (itemId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("itemId"), itemId));
        }
        if (lotId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("lotId"), lotId));
        }
        if (warehouseId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("warehouseId"), warehouseId));
        }
        if (movementType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("movementType"), movementType));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("movedAt"), from.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThan(root.get("movedAt"), to.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)));
        }
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.trim().toLowerCase(java.util.Locale.ROOT);
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("refId")), "%" + term + "%"));
        }
        return spec;
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 200);
    }
}
