package com.genisource.erp.inventory.stock;

import com.genisource.erp.inventory.lot.Lot;
import com.genisource.erp.inventory.lot.LotRepository;
import com.genisource.erp.inventory.warehouse.Warehouse;
import com.genisource.erp.inventory.warehouse.WarehouseRepository;
import com.genisource.erp.masterdata.item.Item;
import com.genisource.erp.masterdata.item.ItemRepository;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.genisource.erp.inventory.warehouse.Location;
import com.genisource.erp.inventory.warehouse.LocationRepository;

/**
 * The live stock balance and the arithmetic that moves it.
 *
 * <p>Reading and writing live together because they share the same lookup: a balance
 * line is identified by warehouse, location, item and lot, and both the screen and the
 * posting routine have to find exactly the same row.
 *
 * <p>Concurrency is handled by the database rather than by locks. The balance row
 * carries a {@code row_version} for optimistic locking, and the unique index over the
 * key stops two postings from creating the same row twice. Both conflicts abort the
 * transaction instead of silently losing an update — a stock system that quietly drops
 * a movement is worse than one that refuses the posting.
 */
@Service
@Transactional(readOnly = true)
public class StockService {

    private final StockRepository stocks;
    private final ItemRepository items;
    private final WarehouseRepository warehouses;
    private final LocationRepository locations;
    private final LotRepository lots;

    public StockService(StockRepository stocks,
                        ItemRepository items,
                        WarehouseRepository warehouses,
                        LocationRepository locations,
                        LotRepository lots) {
        this.stocks = stocks;
        this.items = items;
        this.warehouses = warehouses;
        this.locations = locations;
        this.lots = lots;
    }

    // ------------------------------------------------------------------ read

    public PageResponse<StockDtos.StockView> search(String keyword,
                                                     Long warehouseId,
                                                     Long locationId,
                                                     Long itemId,
                                                     Long lotId,
                                                     boolean includeEmpty,
                                                     int page,
                                                     int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by("id").ascending());
        Page<Stock> result = stocks.findAll(specification(keyword, warehouseId, locationId, itemId,
                lotId, includeEmpty), pageable);
        return PageResponse.of(result, views(result.getContent()));
    }

    /** Stock of one item across every place it sits. */
    public StockDtos.StockSummary summary(Long itemId) {
        Item item = items.findById(itemId)
                .orElseThrow(() -> ApiException.notFound("Item " + itemId + " does not exist"));
        List<Stock> lines = stocks.findAll((root, query, cb) ->
                cb.and(cb.equal(root.get("itemId"), itemId),
                        cb.greaterThan(root.get("onHand"), BigDecimal.ZERO)));
        List<StockDtos.StockView> views = views(lines);

        BigDecimal onHand = sum(lines, Stock::getOnHand);
        BigDecimal reserved = sum(lines, Stock::getReserved);
        BigDecimal locked = sum(lines, Stock::getLocked);
        BigDecimal inTransit = sum(lines, Stock::getInTransit);
        BigDecimal amount = sum(lines, Stock::getTotalAmount);

        return new StockDtos.StockSummary(
                item.getId(),
                item.getCode(),
                item.getName(),
                item.getUomCode(),
                onHand,
                reserved,
                locked,
                inTransit,
                onHand.subtract(reserved).subtract(locked),
                amount,
                lines.size(),
                views);
    }

    /** Total quantity of one batch still on the shelf. */
    public BigDecimal quantityOnHandOfLot(Long lotId) {
        return sum(stocks.findByLotIdAndOnHandGreaterThan(lotId, BigDecimal.ZERO), Stock::getOnHand);
    }

    /** Quantity and value of one batch, as {@code [quantity, amount]}. */
    public BigDecimal[] lotBalance(Long lotId) {
        List<Stock> rows = stocks.findByLotIdAndOnHandGreaterThan(lotId, BigDecimal.ZERO);
        return new BigDecimal[]{sum(rows, Stock::getOnHand), sum(rows, Stock::getTotalAmount)};
    }

    // ----------------------------------------------------------------- write

    /**
     * Adds stock and re-averages the unit cost.
     *
     * @return the balance after the receipt, including the value that entered
     */
    @Transactional
    public StockValuation.Result receive(Long warehouseId,
                                         Long locationId,
                                         Long itemId,
                                         Long lotId,
                                         BigDecimal qty,
                                         BigDecimal unitCost) {
        Stock row = findOrCreate(warehouseId, locationId, itemId, lotId);
        StockValuation.Result result = StockValuation.receive(
                row.getOnHand(), row.getTotalAmount(), qty, unitCost);
        apply(row, result);
        stocks.save(row);
        return result;
    }

    /**
     * Removes stock at the current average cost.
     *
     * <p>Refuses to go below zero. A negative balance is not a small error: it means
     * the book and the shelf disagree, and every later calculation inherits the lie.
     */
    @Transactional
    public StockValuation.Result issue(Long warehouseId,
                                       Long locationId,
                                       Long itemId,
                                       Long lotId,
                                       BigDecimal qty,
                                       String itemLabel) {
        Stock row = findOrCreate(warehouseId, locationId, itemId, lotId);
        BigDecimal available = row.available();
        if (available.compareTo(qty) < 0) {
            throw ApiException.insufficientStock(
                    "Not enough stock of " + itemLabel + " at this location: available "
                            + available.stripTrailingZeros().toPlainString()
                            + ", requested " + qty.stripTrailingZeros().toPlainString());
        }
        StockValuation.Result result = StockValuation.issue(
                row.getOnHand(), row.getTotalAmount(), row.getAvgCost(), qty);
        apply(row, result);
        if (row.getOnHand().signum() == 0
                && row.getReserved().signum() == 0
                && row.getLocked().signum() == 0
                && row.getInTransit().signum() == 0) {
            // Nothing left: drop the row so the balance table stays a list of what is
            // actually on the shelf. The ledger keeps the history either way.
            stocks.delete(row);
        } else {
            stocks.save(row);
        }
        return result;
    }

    // ------------------------------------------------------------- internals

    /**
     * The current unit average of one balance line, or zero when nothing is held.
     *
     * <p>Used where the price is not given but has to be derived: a count that finds
     * more than the book says values the surplus at the average of what is there.
     */
    @Transactional(readOnly = true)
    public BigDecimal currentAverage(Long warehouseId,
                                      Long locationId,
                                      Long itemId,
                                      Long lotId) {
        return stocks.findOne(balanceSpec(warehouseId, locationId, itemId, lotId))
                .map(Stock::getAvgCost)
                .orElse(BigDecimal.ZERO);
    }

    /** How much of one item sits in one place right now, according to the book. */
    @Transactional(readOnly = true)
    public BigDecimal currentQuantity(Long warehouseId,
                                       Long locationId,
                                       Long itemId,
                                       Long lotId) {
        return stocks.findOne(balanceSpec(warehouseId, locationId, itemId, lotId))
                .map(Stock::getOnHand)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Undoes a movement by its exact value.
     *
     * <p>A reversal is not an issue and a receipt: it puts back the quantity and the
     * money the original movement carried. Valuing the reversal at today's average
     * would leave a residue whenever the average moved in between, and the ledger
     * would no longer add up to the balance.
     *
     * @param deltaQty     quantity to add (positive) or remove (negative)
     * @param deltaAmount  value to add (positive) or remove (negative)
     */
    @Transactional
    public StockValuation.Result adjust(Long warehouseId,
                                         Long locationId,
                                         Long itemId,
                                         Long lotId,
                                         BigDecimal deltaQty,
                                         BigDecimal deltaAmount,
                                         String itemLabel) {
        Stock row = findOrCreate(warehouseId, locationId, itemId, lotId);
        BigDecimal newQty = row.getOnHand().add(deltaQty);
        if (newQty.signum() < 0) {
            throw ApiException.insufficientStock("Cannot undo the movement of " + itemLabel
                    + ": only " + row.getOnHand().stripTrailingZeros().toPlainString()
                    + " is still on the shelf");
        }
        BigDecimal newTotal = row.getTotalAmount().add(deltaAmount)
                .setScale(StockValuation.SCALE, java.math.RoundingMode.HALF_UP);
        if (newTotal.signum() < 0) {
            newTotal = BigDecimal.ZERO.setScale(StockValuation.SCALE);
        }
        BigDecimal newAvg = newQty.signum() == 0
                ? BigDecimal.ZERO.setScale(StockValuation.SCALE)
                : newTotal.divide(newQty, StockValuation.SCALE, java.math.RoundingMode.HALF_UP);

        row.setOnHand(newQty);
        row.setTotalAmount(newTotal);
        row.setAvgCost(newAvg);
        if (newQty.signum() == 0
                && row.getReserved().signum() == 0
                && row.getLocked().signum() == 0
                && row.getInTransit().signum() == 0) {
            stocks.delete(row);
        } else {
            stocks.save(row);
        }
        return new StockValuation.Result(newQty, newTotal, newAvg, deltaAmount.abs());
    }
    // ------------------------------------------------------------ reservations

    /**
     * The balance lines of one item in one warehouse that still hold something,
     * fullest first.
     *
     * <p>A caller that wants fifty takes them from the line that holds eighty rather
     * than from the one that holds ten, which is what a picker would do: draining the
     * small leftovers first is how a warehouse ends up with dozens of near-empty bins.
     */
    @Transactional(readOnly = true)
    public List<StockDtos.ReservableLine> reservable(Long warehouseId, Long itemId) {
        return stocks.findAll((root, query, cb) ->
                        cb.and(
                                cb.equal(root.get("warehouseId"), warehouseId),
                                cb.equal(root.get("itemId"), itemId),
                                cb.greaterThan(root.get("onHand"), BigDecimal.ZERO)),
                        Sort.by("id").ascending())
                .stream()
                .map(row -> new StockDtos.ReservableLine(
                        row.getWarehouseId(), row.getLocationId(), row.getItemId(), row.getLotId(),
                        row.getOnHand(), row.available()))
                .filter(line -> line.available().signum() > 0)
                .sorted((left, right) -> right.available().compareTo(left.available()))
                .toList();
    }

    /**
     * Marks part of a balance line as promised.
     *
     * <p>A reservation is a promise, not a movement: nothing leaves the shelf and no
     * ledger entry is written. What changes is what the balance will give away, so a
     * second order cannot sell the same units twice.
     */
    @Transactional
    public void reserve(Long warehouseId,
                        Long locationId,
                        Long itemId,
                        Long lotId,
                        BigDecimal qty,
                        String itemLabel) {
        Stock row = findOrCreate(warehouseId, locationId, itemId, lotId);
        BigDecimal available = row.available();
        if (available.compareTo(qty) < 0) {
            throw ApiException.insufficientStock(
                    "Not enough free stock of " + itemLabel + " to promise: free "
                            + available.stripTrailingZeros().toPlainString()
                            + ", requested " + qty.stripTrailingZeros().toPlainString());
        }
        row.setReserved(row.getReserved().add(qty));
        stocks.save(row);
    }

    /** Gives promised stock back, because the order was cancelled or the goods shipped. */
    @Transactional
    public void releaseReservation(Long warehouseId,
                                   Long locationId,
                                   Long itemId,
                                   Long lotId,
                                   BigDecimal qty,
                                   String itemLabel) {
        Stock row = findOrCreate(warehouseId, locationId, itemId, lotId);
        BigDecimal reserved = row.getReserved();
        if (reserved.compareTo(qty) < 0) {
            throw ApiException.badRequest("Only " + reserved.stripTrailingZeros().toPlainString()
                    + " of " + itemLabel + " is promised, so "
                    + qty.stripTrailingZeros().toPlainString() + " cannot be given back");
        }
        row.setReserved(reserved.subtract(qty));
        stocks.save(row);
    }
    private Stock findOrCreate(Long warehouseId, Long locationId, Long itemId, Long lotId) {
        return stocks.findOne(balanceSpec(warehouseId, locationId, itemId, lotId))
                .orElseGet(() -> new Stock(warehouseId, locationId, itemId, lotId));
    }

    private Specification<Stock> balanceSpec(Long warehouseId,
                                             Long locationId,
                                             Long itemId,
                                             Long lotId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("warehouseId"), warehouseId));
            predicates.add(cb.equal(root.get("itemId"), itemId));
            predicates.add(match(root, cb, "locationId", locationId));
            predicates.add(match(root, cb, "lotId", lotId));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate match(jakarta.persistence.criteria.Root<Stock> root,
                                    jakarta.persistence.criteria.CriteriaBuilder cb,
                                    String attribute,
                                    Long value) {
        if (value == null) {
            return cb.isNull(root.get(attribute));
        }
        return cb.equal(root.get(attribute), value);
    }

    private void apply(Stock row, StockValuation.Result result) {
        row.setOnHand(result.qty());
        row.setTotalAmount(result.totalAmount());
        row.setAvgCost(result.avgCost());
    }

    private Specification<Stock> specification(String keyword,
                                                Long warehouseId,
                                                Long locationId,
                                                Long itemId,
                                                Long lotId,
                                                boolean includeEmpty) {
        Specification<Stock> spec = (root, query, cb) -> cb.conjunction();
        if (!includeEmpty) {
            spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("onHand"), BigDecimal.ZERO));
        }
        if (warehouseId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("warehouseId"), warehouseId));
        }
        if (locationId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("locationId"), locationId));
        }
        if (itemId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("itemId"), itemId));
        }
        if (lotId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("lotId"), lotId));
        }
        if (keyword != null && !keyword.isBlank()) {
            // The search box spans three master lists at once: item codes, batch numbers
            // and warehouse codes. Resolving the hits to ids first keeps the balance
            // query an indexed lookup instead of a join across three tables.
            String term = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            List<Long> itemIds = items.searchIds(term, PageRequest.of(0, 200));
            List<Long> lotIds = lots.searchIds(term);
            List<Long> warehouseIds = warehouses.searchIds(term);
            spec = spec.and((root, query, cb) -> {
                List<Predicate> hits = new ArrayList<>();
                if (!itemIds.isEmpty()) {
                    hits.add(root.get("itemId").in(itemIds));
                }
                if (!lotIds.isEmpty()) {
                    hits.add(root.get("lotId").in(lotIds));
                }
                if (!warehouseIds.isEmpty()) {
                    hits.add(root.get("warehouseId").in(warehouseIds));
                }
                return hits.isEmpty() ? cb.disjunction() : cb.or(hits.toArray(new Predicate[0]));
            });
        }
        return spec;
    }

    List<StockDtos.StockView> views(List<Stock> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        Map<Long, Item> itemById = items
                .findAllById(source.stream().map(Stock::getItemId).distinct().toList())
                .stream().collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<Long, Warehouse> warehouseById = warehouses
                .findAllById(source.stream().map(Stock::getWarehouseId).distinct().toList())
                .stream().collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        Map<Long, Location> locationById = locations
                .findAllById(source.stream().map(Stock::getLocationId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Location::getId, Function.identity()));
        Map<Long, Lot> lotById = lots
                .findAllById(source.stream().map(Stock::getLotId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Lot::getId, Function.identity()));

        return source.stream().map(row -> {
            Item item = itemById.get(row.getItemId());
            Warehouse warehouse = warehouseById.get(row.getWarehouseId());
            Location location = locationById.get(row.getLocationId());
            Lot lot = lotById.get(row.getLotId());
            return new StockDtos.StockView(
                    row.getId(),
                    row.getItemId(),
                    item == null ? null : item.getCode(),
                    item == null ? null : item.getName(),
                    item == null ? null : item.getUomCode(),
                    row.getWarehouseId(),
                    warehouse == null ? null : warehouse.getCode(),
                    warehouse == null ? null : warehouse.getName(),
                    row.getLocationId(),
                    location == null ? null : location.getCode(),
                    location == null ? null : location.getName(),
                    row.getLotId(),
                    lot == null ? null : lot.getLotNo(),
                    lot == null ? null : lot.getLotType().name(),
                    row.getOnHand(),
                    row.getReserved(),
                    row.getInTransit(),
                    row.getLocked(),
                    row.available(),
                    row.getAvgCost(),
                    row.getTotalAmount(),
                    row.getUpdatedAt());
        }).toList();
    }

    private static BigDecimal sum(List<Stock> rows, Function<Stock, BigDecimal> extractor) {
        return rows.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 200);
    }
}
