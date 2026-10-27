package com.genisource.erp.production;

import com.genisource.erp.inventory.document.DocumentDtos;
import com.genisource.erp.inventory.document.DocumentService;
import com.genisource.erp.inventory.document.DocumentType;
import com.genisource.erp.inventory.stock.StockDtos;
import com.genisource.erp.inventory.stock.StockService;
import com.genisource.erp.inventory.warehouse.Location;
import com.genisource.erp.inventory.warehouse.LocationRepository;
import com.genisource.erp.inventory.warehouse.Warehouse;
import com.genisource.erp.inventory.warehouse.WarehouseRepository;
import com.genisource.erp.masterdata.bom.Bom;
import com.genisource.erp.masterdata.bom.BomExplosion;
import com.genisource.erp.masterdata.bom.ComponentRequirement;
import com.genisource.erp.masterdata.item.Item;
import com.genisource.erp.masterdata.item.ItemRepository;
import com.genisource.erp.masterdata.routing.Routing;
import com.genisource.erp.masterdata.routing.RoutingOperation;
import com.genisource.erp.masterdata.routing.RoutingRepository;
import com.genisource.erp.masterdata.workcenter.WorkCenter;
import com.genisource.erp.masterdata.workcenter.WorkCenterRepository;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.PageResponse;
import com.genisource.erp.platform.common.RecordStatus;
import com.genisource.erp.platform.common.RevisionStatus;
import com.genisource.erp.platform.numbering.DocumentNoAllocator;
import com.genisource.erp.platform.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Works orders: raise one against a frozen BOM, release it so its materials are held,
 * issue those materials, and book the finished goods back in.
 *
 * <p>Three steps rather than one because each is a different promise. Raising says "we
 * intend to make this". Releasing says "the material is set aside, the floor may start".
 * Issuing and completing move real stock. A factory that collapsed the first two into
 * one would commit stock for every order a planner sketched, and the orders that
 * actually have to run would find the shelves empty.
 *
 * <p>Issues and completions are not documents of their own — they are stock documents
 * carrying {@code PRD_ORDER} as their source, so the inventory ledger stays the single
 * place that answers "where did this material go".
 */
@Service
@Transactional(readOnly = true)
public class ProductionService {

    /** What a stock document says about where it came from. */
    public static final String SOURCE_TYPE = "PRD_ORDER";

    /** What an order raised from a material plan records as its origin. */
    public static final String SOURCE_TYPE_MRP_PLAN = "MRP_PLAN";

    private static final String DOC_PREFIX = "MO";

    /** Refuses to walk a BOM deeper than this; a cycle would otherwise never terminate. */
    private static final int MAX_BOM_DEPTH = 18;

    private final ProductionOrderRepository orders;
    private final ProductionOperationRepository operationRows;
    private final ItemRepository items;
    private final WarehouseRepository warehouses;
    private final LocationRepository locations;
    private final WorkCenterRepository workCenters;
    private final BomExplosion explosion;
    private final RoutingRepository routings;
    private final StockService stock;
    private final DocumentService stockDocuments;
    private final DocumentNoAllocator numbers;
    private final AuditService audit;

    public ProductionService(ProductionOrderRepository orders,
                             ProductionOperationRepository operationRows,
                             ItemRepository items,
                             WarehouseRepository warehouses,
                             LocationRepository locations,
                             WorkCenterRepository workCenters,
                             BomExplosion explosion,
                             RoutingRepository routings,
                             StockService stock,
                             DocumentService stockDocuments,
                             DocumentNoAllocator numbers,
                             AuditService audit) {
        this.orders = orders;
        this.operationRows = operationRows;
        this.items = items;
        this.warehouses = warehouses;
        this.locations = locations;
        this.workCenters = workCenters;
        this.explosion = explosion;
        this.routings = routings;
        this.stock = stock;
        this.stockDocuments = stockDocuments;
        this.numbers = numbers;
        this.audit = audit;
    }

    // ------------------------------------------------------------------- read

    public PageResponse<ProductionDtos.OrderView> search(String keyword,
                                                         Long itemId,
                                                         ProductionOrderStatus status,
                                                         LocalDate from,
                                                         LocalDate to,
                                                         int page,
                                                         int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by("id").descending());
        Page<ProductionOrder> result = orders.findAll(
                specification(keyword, itemId, status, from, to), pageable);
        return PageResponse.of(result, views(result.getContent()));
    }

    public ProductionDtos.OrderView get(Long id) {
        return views(List.of(require(id))).get(0);
    }

    /**
     * Work another order is going to do, for the material plan to net against.
     *
     * <p>Released and in-progress orders only. A planned order is an intention that the
     * next run will regenerate anyway, so counting it as supply would quietly cancel the
     * shortage it was raised to cover.
     */
    public List<OpenSupplyLine> openSupply() {
        List<ProductionOrder> open = orders.findByStatusInOrderByIdAsc(
                List.of(ProductionOrderStatus.RELEASED, ProductionOrderStatus.IN_PROGRESS));
        List<OpenSupplyLine> supply = new ArrayList<>();
        for (ProductionOrder order : open) {
            BigDecimal remaining = order.remainingQty();
            if (remaining.signum() <= 0) {
                continue;
            }
            LocalDate due = order.getPlannedEnd() == null
                    ? (order.getPlannedStart() == null ? LocalDate.now() : order.getPlannedStart())
                    : order.getPlannedEnd();
            supply.add(new OpenSupplyLine(order.getItemId(), remaining, due, order.getDocNo()));
        }
        return supply;
    }

    // ------------------------------------------------------------------ write

    @Transactional
    public ProductionDtos.OrderView create(ProductionDtos.OrderRequest request) {
        Item item = requireItem(request.itemId());
        LocalDate start = request.plannedStart() == null ? LocalDate.now() : request.plannedStart();
        ProductionOrder order = new ProductionOrder(
                numbers.next(DOC_PREFIX, start), item.getId(), request.qtyPlanned());
        apply(order, request, item);
        orders.save(order);

        audit.record("production.order.create", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "prd_production_order",
                String.valueOf(order.getId()), Map.of("docNo", order.getDocNo()));
        return views(List.of(order)).get(0);
    }

    @Transactional
    public ProductionDtos.OrderView update(Long id, ProductionDtos.OrderRequest request) {
        ProductionOrder order = require(id);
        if (!order.getStatus().isEditable()) {
            throw ApiException.invalidState("Order " + order.getDocNo() + " is "
                    + order.getStatus().name() + " and can no longer be edited");
        }
        Item item = requireItem(request.itemId());
        apply(order, request, item);
        orders.save(order);

        audit.record("production.order.update", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "prd_production_order", String.valueOf(id),
                Map.of("docNo", order.getDocNo()));
        return views(List.of(order)).get(0);
    }

    @Transactional
    public void delete(Long id) {
        ProductionOrder order = require(id);
        if (order.getStatus() != ProductionOrderStatus.PLANNED) {
            throw ApiException.invalidState("Order " + order.getDocNo() + " is "
                    + order.getStatus().name() + "; cancel it instead of deleting it");
        }
        orders.delete(order);
        audit.record("production.order.delete", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "prd_production_order", String.valueOf(id),
                Map.of("docNo", order.getDocNo()));
    }

    /**
     * Raises an order straight from a material plan row.
     *
     * <p>Goes through {@link #create} so the BOM expansion, the routing copy and the
     * numbering behave exactly as they do for a hand-raised order, then stamps the plan
     * row it came from on the same transaction.
     */
    @Transactional
    public ProductionDtos.OrderView createPlannedOrder(PlannedProductionOrder plan) {
        ProductionDtos.OrderRequest request = new ProductionDtos.OrderRequest(
                plan.itemId(), plan.qty(), null, plan.warehouseId(), null,
                plan.dueDate(), plan.dueDate(), plan.remark(), List.of());
        ProductionDtos.OrderView created = create(request);

        ProductionOrder order = require(created.id());
        order.setSourceType(SOURCE_TYPE_MRP_PLAN);
        order.setSourceId(plan.sourceId());
        orders.save(order);

        audit.record("production.order.from-plan", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "prd_production_order",
                String.valueOf(order.getId()),
                Map.of("docNo", order.getDocNo(), "plan", String.valueOf(plan.sourceId())));
        return views(List.of(order)).get(0);
    }

    // ------------------------------------------------------------- life cycle

    /**
     * Hands the order to the floor and sets its material aside.
     *
     * <p>Locking is best effort. An order short of two screws is still worth releasing —
     * refusing would stop a job that can start, and the shortage is visible on the line
     * as the difference between what is required and what is held. Where the issue
     * eventually cannot be covered, {@link #issue} fails at the point where it matters.
     */
    @Transactional
    public ProductionDtos.OrderView release(Long id) {
        ProductionOrder order = require(id);
        if (order.getStatus() != ProductionOrderStatus.PLANNED) {
            throw ApiException.invalidState("Order " + order.getDocNo() + " is "
                    + order.getStatus().name() + " and cannot be released again");
        }
        Long warehouseId = order.getWarehouseId();
        if (warehouseId == null) {
            throw ApiException.badRequest("Order " + order.getDocNo()
                    + " needs the warehouse it will draw material from before it can be released");
        }
        requireWarehouse(warehouseId);

        for (ProductionOrderLine line : order.getLines()) {
            BigDecimal wanted = line.lockableQty();
            if (wanted.signum() <= 0) {
                continue;
            }
            Long lineWarehouse = line.getWarehouseId() == null
                    ? warehouseId : line.getWarehouseId();
            BigDecimal remaining = wanted;
            for (StockDtos.ReservableLine candidate
                    : stock.reservable(lineWarehouse, line.getItemId())) {
                if (remaining.signum() <= 0) {
                    break;
                }
                BigDecimal take = candidate.available().min(remaining);
                if (take.signum() <= 0) {
                    continue;
                }
                stock.lock(candidate.warehouseId(), candidate.locationId(), candidate.itemId(),
                        candidate.lotId(), take, itemCode(line.getItemId()));
                line.lock(take);
                remaining = remaining.subtract(take);
            }
        }

        order.release();
        orders.save(order);
        audit.record("production.order.release", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "prd_production_order", String.valueOf(id),
                Map.of("docNo", order.getDocNo()));
        return views(List.of(order)).get(0);
    }

    /**
     * Takes material to the line and books it out of stock.
     *
     * <p>What the order holds is given back <em>before</em> the stock is issued, exactly
     * as a shipment releases its reservation first: an issue is validated against what is
     * free, and stock still held for this very order is not free.
     */
    @Transactional
    public ProductionDtos.OrderView issue(Long id, ProductionDtos.IssueRequest request) {
        ProductionOrder order = require(id);
        if (!order.getStatus().acceptsIssues()) {
            throw ApiException.invalidState("Order " + order.getDocNo() + " is "
                    + order.getStatus().name() + "; release it before issuing material");
        }
        Long orderWarehouse = order.getWarehouseId();
        if (orderWarehouse == null) {
            throw ApiException.badRequest("Order " + order.getDocNo() + " has no warehouse");
        }

        Map<BalanceKey, List<DocumentDtos.LineRequest>> byBalance = new LinkedHashMap<>();        for (ProductionDtos.IssueLine pick : request.lines()) {
            ProductionOrderLine line = order.getLines().stream()
                    .filter(candidate -> candidate.getId().equals(pick.orderLineId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Line " + pick.orderLineId()
                            + " is not part of " + order.getDocNo()));
            BigDecimal open = line.openQty();
            if (pick.qty().compareTo(open) > 0) {
                throw ApiException.badRequest("Only " + open.stripTrailingZeros().toPlainString()
                        + " of " + itemCode(line.getItemId()) + " is still open on "
                        + order.getDocNo());
            }
            Long lineWarehouse = line.getWarehouseId() == null
                    ? orderWarehouse : line.getWarehouseId();

            BigDecimal toUnlock = pick.qty().min(line.getQtyLocked());
            if (toUnlock.signum() > 0) {
                BigDecimal unlocked = stock.unlockAcross(lineWarehouse, line.getItemId(), toUnlock,
                        itemCode(line.getItemId()));
                line.releaseLock(unlocked);
            }

            BigDecimal remaining = pick.qty();
            for (StockDtos.ReservableLine candidate
                    : stock.reservable(lineWarehouse, line.getItemId())) {
                if (remaining.signum() <= 0) {
                    break;
                }
                if (pick.lotId() != null && !pick.lotId().equals(candidate.lotId())) {
                    continue;
                }
                BigDecimal take = candidate.available().min(remaining);
                if (take.signum() <= 0) {
                    continue;
                }
                byBalance.computeIfAbsent(
                                new BalanceKey(candidate.warehouseId(), candidate.locationId()),
                                key -> new ArrayList<>())
                        .add(new DocumentDtos.LineRequest(candidate.itemId(), candidate.lotId(),
                                take, null, null, line.getRemark()));
                remaining = remaining.subtract(take);
            }
            if (remaining.signum() > 0) {
                throw ApiException.insufficientStock(
                        "Not enough free stock of " + itemCode(line.getItemId()) + " to issue "
                                + pick.qty().stripTrailingZeros().toPlainString()
                                + " to " + order.getDocNo() + "; "
                                + pick.qty().subtract(remaining).stripTrailingZeros().toPlainString()
                                + " is available");
            }
            line.issue(pick.qty());
        }

        for (Map.Entry<BalanceKey, List<DocumentDtos.LineRequest>> entry : byBalance.entrySet()) {
            DocumentDtos.DocumentRequest stockRequest = new DocumentDtos.DocumentRequest(
                    DocumentType.OUTBOUND, entry.getKey().warehouseId(), entry.getKey().locationId(),
                    null, null, null, request.bizDate(),
                    "Issued to production order " + order.getDocNo(), entry.getValue());
            stockDocuments.postSourced(stockRequest, SOURCE_TYPE, order.getDocNo());
        }

        order.markStarted();
        orders.save(order);
        audit.record("production.order.issue", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "prd_production_order", String.valueOf(id),
                Map.of("docNo", order.getDocNo(), "lines", String.valueOf(request.lines().size())));
        return views(List.of(order)).get(0);
    }

    /**
     * Books finished goods in against the order.
     *
     * <p>Valued at the item's standard cost. Costing the batch from what it actually
     * consumed needs work in progress and variance accounts, which is M5; using the
     * standard cost now keeps the finished-goods value out of the average-cost history
     * until there is a defensible number to put in it.
     */
    @Transactional
    public ProductionDtos.OrderView complete(Long id, ProductionDtos.CompleteRequest request) {
        ProductionOrder order = require(id);
        if (!order.getStatus().acceptsCompletions()) {
            throw ApiException.invalidState("Order " + order.getDocNo() + " is "
                    + order.getStatus().name() + " and cannot take a completion");
        }
        BigDecimal remaining = order.remainingQty();
        if (request.qty().compareTo(remaining) > 0) {
            throw ApiException.badRequest("Only "
                    + remaining.stripTrailingZeros().toPlainString() + " of "
                    + itemCode(order.getItemId()) + " remains to be completed on "
                    + order.getDocNo());
        }
        Warehouse warehouse = requireWarehouse(request.warehouseId());
        if (request.locationId() != null) {
            requireLocation(request.locationId(), warehouse.getId());
        }
        Item item = requireItem(order.getItemId());
        BigDecimal unitCost = item.getStandardCost() == null
                ? BigDecimal.ZERO : item.getStandardCost();

        DocumentDtos.DocumentRequest stockRequest = new DocumentDtos.DocumentRequest(
                DocumentType.INBOUND, warehouse.getId(), request.locationId(), null, null, null,
                request.bizDate(), "Completed from production order " + order.getDocNo(),
                List.of(new DocumentDtos.LineRequest(order.getItemId(), null, request.qty(),
                        unitCost, null, blankToNull(request.remark()))));
        stockDocuments.postSourced(stockRequest, SOURCE_TYPE, order.getDocNo());

        order.markStarted();
        order.registerCompletion(request.qty());
        if (order.getStatus() == ProductionOrderStatus.COMPLETED) {
            order.getOperations().forEach(ProductionOrderOperation::markDone);
        }
        orders.save(order);
        audit.record("production.order.complete", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "prd_production_order", String.valueOf(id),
                Map.of("docNo", order.getDocNo(), "qty", request.qty().toPlainString()));
        return views(List.of(order)).get(0);
    }

    /** Wraps up an order that will not be finished; the material it held goes back. */
    @Transactional
    public ProductionDtos.OrderView close(Long id) {
        ProductionOrder order = require(id);
        if (order.getStatus() == ProductionOrderStatus.CLOSED) {
            throw ApiException.invalidState("Order " + order.getDocNo() + " is already closed");
        }
        if (order.getStatus() == ProductionOrderStatus.CANCELLED) {
            throw ApiException.invalidState("Order " + order.getDocNo()
                    + " was cancelled and cannot be closed");
        }
        releaseLocks(order);
        order.close();
        orders.save(order);
        audit.record("production.order.close", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "prd_production_order", String.valueOf(id),
                Map.of("docNo", order.getDocNo()));
        return views(List.of(order)).get(0);
    }

    /** Abandons an order that never consumed anything. */
    @Transactional
    public ProductionDtos.OrderView cancel(Long id) {
        ProductionOrder order = require(id);
        if (order.getStatus().isTerminal()) {
            throw ApiException.invalidState("Order " + order.getDocNo() + " is "
                    + order.getStatus().name() + " and cannot be cancelled");
        }
        boolean consumed = order.getLines().stream()
                .anyMatch(line -> line.getQtyIssued().signum() > 0);
        if (consumed) {
            throw ApiException.invalidState("Order " + order.getDocNo()
                    + " has already consumed material; close it instead of cancelling it"
                    + " so the issue stays on the books");
        }
        releaseLocks(order);
        order.cancel();
        orders.save(order);
        audit.record("production.order.cancel", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "prd_production_order", String.valueOf(id),
                Map.of("docNo", order.getDocNo()));
        return views(List.of(order)).get(0);
    }

    // ---------------------------------------------------------------- helpers

    /** Gives back everything the order is still holding. */
    private void releaseLocks(ProductionOrder order) {
        for (ProductionOrderLine line : order.getLines()) {
            BigDecimal held = line.getQtyLocked();
            if (held.signum() <= 0) {
                continue;
            }
            Long lineWarehouse = line.getWarehouseId() == null
                    ? order.getWarehouseId() : line.getWarehouseId();
            if (lineWarehouse == null) {
                continue;
            }
            BigDecimal unlocked = stock.unlockAcross(lineWarehouse, line.getItemId(), held,
                    itemCode(line.getItemId()));
            line.releaseLock(unlocked);
        }
    }

    private void apply(ProductionOrder order,
                       ProductionDtos.OrderRequest request,
                       Item item) {
        Bom bom = resolveBom(item.getId(), request.bomId());
        order.setBomId(bom == null ? null : bom.getId());
        order.setBomVersion(bom == null ? null : bom.getVersion());

        Warehouse warehouse = request.warehouseId() == null
                ? null : requireWarehouse(request.warehouseId());
        if (request.locationId() != null && warehouse != null) {
            requireLocation(request.locationId(), warehouse.getId());
        }
        order.setWarehouseId(warehouse == null ? null : warehouse.getId());
        order.setLocationId(request.locationId());
        order.setPlannedStart(request.plannedStart());
        order.setPlannedEnd(request.plannedEnd());
        order.setRemark(blankToNull(request.remark()));
        order.replaceLines(buildLines(request, item, bom));
        order.replaceOperations(buildOperations(item, request.qtyPlanned()));
    }

    private List<ProductionOrderLine> buildLines(ProductionDtos.OrderRequest request,
                                                 Item item,
                                                 Bom bom) {
        List<ProductionOrderLine> lines = new ArrayList<>();
        if (request.lines() != null && !request.lines().isEmpty()) {
            int index = 0;
            for (ProductionDtos.LineRequest line : request.lines()) {
                if (!items.existsById(line.itemId())) {
                    throw ApiException.badRequest("Item " + line.itemId() + " does not exist");
                }
                lines.add(new ProductionOrderLine(++index * 10, line.itemId(),
                        line.qtyRequired(), line.warehouseId(), blankToNull(line.remark())));
            }
            return lines;
        }
        if (bom == null) {
            throw ApiException.badRequest("Item " + item.getCode()
                    + " has no released bill of materials, so the components to consume have"
                    + " to be listed explicitly");
        }
        Map<Long, BigDecimal> components =
                componentQuantities(item.getId(), request.qtyPlanned(), 0);
        if (components.isEmpty()) {
            throw ApiException.badRequest("Bill of materials " + bom.getVersion()
                    + " for " + item.getCode() + " has no components");
        }
        int index = 0;
        for (Map.Entry<Long, BigDecimal> entry : components.entrySet()) {
            lines.add(new ProductionOrderLine(++index * 10, entry.getKey(), entry.getValue(),
                    null, null));
        }
        return lines;
    }

    /**
     * Flattens the BOM into "how much of each component, in total".
     *
     * <p>A phantom line is not stocked: it is a convenient grouping of parts, so its own
     * components are consumed in its place. Flattening here rather than carrying the
     * phantom onto the order is what keeps the picking list free of items that cannot be
     * picked.
     */
    private Map<Long, BigDecimal> componentQuantities(Long itemId, BigDecimal qty, int depth) {
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        if (depth > MAX_BOM_DEPTH) {
            return totals;
        }
        for (ComponentRequirement requirement : explosion.directComponents(itemId, qty)) {
            if (requirement.phantom()) {
                componentQuantities(requirement.itemId(), requirement.qty(), depth + 1)
                        .forEach((componentId, componentQty) ->
                                totals.merge(componentId, componentQty, BigDecimal::add));
            } else {
                totals.merge(requirement.itemId(), requirement.qty(), BigDecimal::add);
            }
        }
        return totals;
    }

    /** Copies the released route onto the order, scaling per-unit run time by the quantity. */
    private List<ProductionOrderOperation> buildOperations(Item item, BigDecimal qty) {
        List<Routing> released = routings.findByItemIdAndStatus(item.getId(),
                RevisionStatus.RELEASED);
        if (released.isEmpty()) {
            return List.of();
        }
        Routing routing = released.stream()
                .max((left, right) -> left.getVersion().compareTo(right.getVersion()))
                .orElseThrow();
        Routing loaded = routings.findWithOperationsById(routing.getId()).orElse(routing);

        List<ProductionOrderOperation> operations = new ArrayList<>();
        for (RoutingOperation operation : loaded.getOperations()) {
            BigDecimal run = operation.getRunMinutesPerUnit().multiply(qty)
                    .setScale(2, RoundingMode.HALF_UP);
            operations.add(new ProductionOrderOperation(operation.getSeq(), operation.getName(),
                    operation.getWorkCenterId(), operation.getSetupMinutes(), run));
        }
        return operations;
    }

    private Bom resolveBom(Long itemId, Long requestedBomId) {
        Bom released = explosion.releasedBomOf(itemId).orElse(null);
        if (requestedBomId == null) {
            return released;
        }
        if (released == null || !released.getId().equals(requestedBomId)) {
            throw ApiException.badRequest("Only the released bill of materials of this item"
                    + " can be frozen onto a works order; " + requestedBomId + " is not it");
        }
        return released;
    }

    /** Which balance line a group of issue lines draws from. */
    private record BalanceKey(Long warehouseId, Long locationId) {
    }

    private String itemCode(Long itemId) {
        return items.findById(itemId).map(Item::getCode).orElse("item " + itemId);
    }

    private Item requireItem(Long id) {
        Item item = items.findById(id)
                .orElseThrow(() -> ApiException.notFound("Item " + id + " does not exist"));
        if (item.getStatus() != RecordStatus.ACTIVE) {
            throw ApiException.invalidState("Item " + item.getCode() + " is inactive");
        }
        return item;
    }

    private Warehouse requireWarehouse(Long id) {
        Warehouse warehouse = warehouses.findById(id)
                .orElseThrow(() -> ApiException.notFound("Warehouse " + id + " does not exist"));
        if (warehouse.getStatus() != RecordStatus.ACTIVE) {
            throw ApiException.invalidState("Warehouse " + warehouse.getCode() + " is inactive");
        }
        return warehouse;
    }

    private void requireLocation(Long id, Long warehouseId) {
        Location location = locations.findById(id)
                .orElseThrow(() -> ApiException.notFound("Location " + id + " does not exist"));
        if (!location.getWarehouseId().equals(warehouseId)) {
            throw ApiException.badRequest("Location " + location.getCode()
                    + " does not belong to this warehouse");
        }
    }

    private ProductionOrder require(Long id) {
        return orders.findWithLinesById(id)
                .orElseThrow(() -> ApiException.notFound(
                        "Production order " + id + " does not exist"));
    }

    private Specification<ProductionOrder> specification(String keyword,
                                                         Long itemId,
                                                         ProductionOrderStatus status,
                                                         LocalDate from,
                                                         LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (itemId != null) {
                predicates.add(cb.equal(root.get("itemId"), itemId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("plannedEnd"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("plannedStart"), to));
            }
            if (keyword != null && !keyword.isBlank()) {
                String term = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("docNo")), term),
                        cb.like(cb.lower(root.get("remark")), term)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    List<ProductionDtos.OrderView> views(List<ProductionOrder> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        Map<Long, Item> itemById = items
                .findAllById(source.stream().map(ProductionOrder::getItemId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Item::getId, Function.identity()));
        // Components are needed for the item names, not just the finished item.
        Map<Long, Item> componentById = items
                .findAllById(source.stream().flatMap(order -> order.getLines().stream())
                        .map(ProductionOrderLine::getItemId).distinct().toList())
                .stream().collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<Long, Warehouse> warehouseById = warehouses
                .findAllById(source.stream().flatMap(order -> {
                    List<Long> ids = new ArrayList<>();
                    ids.add(order.getWarehouseId());
                    order.getLines().forEach(line -> ids.add(line.getWarehouseId()));
                    return ids.stream();
                }).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        Map<Long, Location> locationById = locations
                .findAllById(source.stream().map(ProductionOrder::getLocationId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Location::getId, Function.identity()));

        // One query for the whole page, then grouped: Hibernate refuses to join both
        // collections in a single statement, and asking per order would be one round trip
        // per row on a list screen.
        Map<Long, List<ProductionOrderOperation>> operationsByOrder = operationRows
                .findByOrder_IdIn(source.stream().map(ProductionOrder::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        operation -> operation.getOrder().getId(),
                        LinkedHashMap::new, Collectors.toList()));

        Map<Long, WorkCenter> workCenterById = workCenters
                .findAllById(operationsByOrder.values().stream()
                        .flatMap(List::stream)
                        .map(ProductionOrderOperation::getWorkCenterId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(WorkCenter::getId, Function.identity()));

        return source.stream().map(order -> {
            Item item = itemById.get(order.getItemId());
            Warehouse warehouse = warehouseById.get(order.getWarehouseId());
            Location location = locationById.get(order.getLocationId());
            return new ProductionDtos.OrderView(
                    order.getId(),
                    order.getDocNo(),
                    order.getItemId(),
                    item == null ? null : item.getCode(),
                    item == null ? null : item.getName(),
                    item == null ? null : item.getUomCode(),
                    order.getBomId(),
                    order.getBomVersion(),
                    order.getQtyPlanned(),
                    order.getQtyCompleted(),
                    order.remainingQty(),
                    order.getStatus(),
                    order.getWarehouseId(),
                    warehouse == null ? null : warehouse.getCode(),
                    order.getLocationId(),
                    location == null ? null : location.getName(),
                    order.getPlannedStart(),
                    order.getPlannedEnd(),
                    order.getActualStart(),
                    order.getActualEnd(),
                    order.getMesWoNo(),
                    order.getSourceType(),
                    order.getSourceId(),
                    order.getRemark(),
                    order.getRowVersion(),
                    order.getLines().stream().map(line -> {
                        Item component = componentById.get(line.getItemId());
                        Warehouse lineWarehouse = warehouseById.get(line.getWarehouseId());
                        return new ProductionDtos.LineView(
                                line.getId(), line.getLineNo(), line.getItemId(),
                                component == null ? null : component.getCode(),
                                component == null ? null : component.getName(),
                                component == null ? null : component.getUomCode(),
                                line.getQtyRequired(), line.getQtyIssued(), line.getQtyLocked(),
                                line.openQty(),
                                line.getWarehouseId(),
                                lineWarehouse == null ? null : lineWarehouse.getCode(),
                                line.getRemark());
                    }).toList(),
                    operationsByOrder.getOrDefault(order.getId(), List.of()).stream()
                            .map(operation -> {
                                WorkCenter center = workCenterById.get(operation.getWorkCenterId());
                                return new ProductionDtos.OperationView(
                                        operation.getSequence(), operation.getName(),
                                        operation.getWorkCenterId(),
                                        center == null ? null : center.getCode(),
                                        operation.getSetupMinutes(), operation.getRunMinutes(),
                                        operation.getStatus(), operation.getCompletedAt());
                            }).toList());
        }).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 200);
    }
}
