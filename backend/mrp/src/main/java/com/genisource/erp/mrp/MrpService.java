package com.genisource.erp.mrp;

import com.genisource.erp.inventory.stock.ItemBalance;
import com.genisource.erp.inventory.stock.StockService;
import com.genisource.erp.masterdata.bom.BomExplosion;
import com.genisource.erp.masterdata.bom.ComponentRequirement;
import com.genisource.erp.masterdata.item.Item;
import com.genisource.erp.masterdata.item.ItemRepository;
import com.genisource.erp.masterdata.partner.Partner;
import com.genisource.erp.masterdata.partner.PartnerRepository;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.PageResponse;
import com.genisource.erp.platform.numbering.DocumentNoAllocator;
import com.genisource.erp.platform.security.CurrentUser;
import com.genisource.erp.production.OpenSupplyLine;
import com.genisource.erp.production.PlannedProductionOrder;
import com.genisource.erp.production.ProductionDtos;
import com.genisource.erp.production.ProductionService;
import com.genisource.erp.purchase.PlannedPurchase;
import com.genisource.erp.purchase.PurchaseDtos;
import com.genisource.erp.purchase.PurchaseService;
import com.genisource.erp.sales.OpenDemandLine;
import com.genisource.erp.sales.SalesService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Material requirements planning: what is missing, and what to do about it.
 *
 * <p>The calculation runs one level at a time. Sales demand is netted against stock and
 * open supply; whatever is still short becomes a recommendation — buy it or make it — and
 * only a make decision explodes into the next level. Settling each level before asking
 * what it is made of is what stops the engine ordering components for a sub-assembly that
 * turns out to be sitting in the warehouse in sufficient quantity.
 *
 * <p>A run is a snapshot and is never rewritten. Everything it looked at is recorded with
 * the numbers it used, because a planner's follow-up question is "why", not "what".
 */
@Service
@Transactional(readOnly = true)
public class MrpService {

    private static final String RUN_PREFIX = "MRP";
    private static final int DEFAULT_HORIZON_DAYS = 30;
    private static final int DEFAULT_MAX_LEVELS = 12;

    /** What a purchase order raised from a plan row records as its origin. */
    private static final String CONVERTED_PURCHASE = "PURCHASE_ORDER";

    /** What a works order raised from a plan row records as its origin. */
    private static final String CONVERTED_PRODUCTION = "PRODUCTION_ORDER";

    private final MrpRunRepository runs;
    private final MrpDemandRepository demandRows;
    private final MrpPlanSupplyRepository planRows;
    private final ItemRepository items;
    private final PartnerRepository partners;
    private final SalesService sales;
    private final PurchaseService purchase;
    private final ProductionService production;
    private final StockService stock;
    private final BomExplosion explosion;
    private final DocumentNoAllocator numbers;
    private final AuditService audit;

    public MrpService(MrpRunRepository runs,
                      MrpDemandRepository demandRows,
                      MrpPlanSupplyRepository planRows,
                      ItemRepository items,
                      PartnerRepository partners,
                      SalesService sales,
                      PurchaseService purchase,
                      ProductionService production,
                      StockService stock,
                      BomExplosion explosion,
                      DocumentNoAllocator numbers,
                      AuditService audit) {
        this.runs = runs;
        this.demandRows = demandRows;
        this.planRows = planRows;
        this.items = items;
        this.partners = partners;
        this.sales = sales;
        this.purchase = purchase;
        this.production = production;
        this.stock = stock;
        this.explosion = explosion;
        this.numbers = numbers;
        this.audit = audit;
    }

    // ------------------------------------------------------------------- read

    public PageResponse<MrpDtos.RunView> search(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 200), Sort.by("id").descending());
        Page<MrpRun> result = runs.findAll(pageable);
        return PageResponse.of(result, result.getContent().stream().map(this::runView).toList());
    }

    public MrpDtos.RunDetail get(Long id) {
        MrpRun run = requireRun(id);
        return new MrpDtos.RunDetail(
                runView(run),
                demandViews(demandRows.findByRunIdOrderByItemLevelAscItemIdAsc(id)),
                planViews(planRows.findByRunIdOrderBySupplyTypeAscItemIdAsc(id)));
    }

    // -------------------------------------------------------------------- run

    /**
     * Runs the calculation, end to end, in one transaction.
     *
     * <p>Nothing is written until the numbers are settled, so a run that fails halfway
     * leaves no half-plan behind for somebody to act on.
     */
    @Transactional
    public MrpDtos.RunDetail run(MrpDtos.RunRequest request) {
        LocalDate today = LocalDate.now();
        int horizon = request.horizonDays() == null
                ? DEFAULT_HORIZON_DAYS : Math.max(request.horizonDays(), 1);
        LocalDate from = request.demandFrom() == null ? today : request.demandFrom();
        LocalDate to = request.demandTo() == null ? from.plusDays(horizon) : request.demandTo();
        if (to.isBefore(from)) {
            throw ApiException.badRequest("The demand window ends before it starts: "
                    + to + " is earlier than " + from);
        }
        boolean includeSafety = request.includeSafety() == null || request.includeSafety();
        int maxLevels = request.maxLevels() == null
                ? DEFAULT_MAX_LEVELS : Math.min(Math.max(request.maxLevels(), 1), 20);

        MrpRun run = new MrpRun(numbers.next(RUN_PREFIX, today), horizon, from, to,
                includeSafety, maxLevels, CurrentUser.username().orElse("system"),
                blankToNull(request.remark()));
        runs.save(run);

        Map<Long, Bucket> buckets = seedFromSales(from, to);
        Map<Long, BigDecimal> inTransit = openSupplyByItem();
        // Stock promised to orders outside the window: the only reservation the plan has
        // to add, because inside the window the promise and the demand cancel out.
        Map<Long, BigDecimal> reservedOutside = sales.reservedOutsideWindow(from, to);
        Map<Long, ItemBalance> balances = stock.balanceByItem();

        Map<Integer, List<Long>> itemsByLevel = new TreeMap<>();
        itemsByLevel.put(0, new ArrayList<>(buckets.keySet()));

        Map<Long, MrpDemand> demands = new LinkedHashMap<>();
        Map<Long, MrpPlanSupply> plans = new LinkedHashMap<>();
        Set<Long> exploded = new HashSet<>();

        for (int level = 0; level <= maxLevels; level++) {
            List<Long> atThisLevel = itemsByLevel.get(level);
            if (atThisLevel == null || atThisLevel.isEmpty()) {
                continue;
            }
            for (Long itemId : atThisLevel) {
                Item item = items.findById(itemId).orElse(null);
                if (item == null) {
                    continue;
                }
                Bucket bucket = buckets.get(itemId);
                if (bucket == null) {
                    continue;
                }
                ItemBalance balance = balances.get(itemId);
                BigDecimal onHand = balance == null ? BigDecimal.ZERO : balance.onHand();
                BigDecimal locked = balance == null ? BigDecimal.ZERO : balance.locked();
                BigDecimal transit = inTransit.getOrDefault(itemId, BigDecimal.ZERO);
                BigDecimal reserved = reservedOutside.getOrDefault(itemId, BigDecimal.ZERO);
                BigDecimal safety = includeSafety ? orZero(item.getSafetyStock()) : BigDecimal.ZERO;
                BigDecimal gross = bucket.fromSales.add(bucket.fromComponents);

                // net = gross + safety + reserved outside the window − on hand
                //       − locked − in transit
                BigDecimal net = gross.add(safety).add(reserved)
                        .subtract(onHand).subtract(locked).subtract(transit);

                MrpDemand demand = demands.computeIfAbsent(itemId,
                        id -> new MrpDemand(run.getId(), id, bucket.level));
                demand.snapshot(gross, onHand, reserved, locked, transit, safety, net,
                        bucket.requiredDate, bucket.fromSales, bucket.fromComponents);

                if (net.signum() <= 0) {
                    continue;
                }
                MrpSupplyType supplyType = supplyTypeOf(item);
                if (supplyType == null) {
                    // Nothing to recommend: not purchased and not buildable. The shortage
                    // row is still written, so the item does not silently vanish.
                    continue;
                }

                BigDecimal multiple = orderMultipleOf(item, supplyType);
                BigDecimal orderQty = roundUp(net, multiple);
                LocalDate due = bucket.requiredDate == null ? to : bucket.requiredDate;
                int leadTime = supplyType == MrpSupplyType.PURCHASE
                        ? Math.max(item.getPurchaseLeadTimeDays(), 0) : 0;
                LocalDate startDate = due.minusDays(leadTime);

                MrpPlanSupply plan = plans.computeIfAbsent(itemId,
                        id -> new MrpPlanSupply(run.getId(), id, supplyType));
                plan.recommend(net, orderQty, multiple,
                        supplyType == MrpSupplyType.PURCHASE ? item.getDefaultSupplierId() : null,
                        leadTime, startDate, due);

                // Components are planned for what will actually be built, not for the raw
                // shortfall: nobody builds 4.3 units.
                if (supplyType == MrpSupplyType.PRODUCTION && exploded.add(itemId)) {
                    explodeInto(buckets, itemsByLevel, level + 1, itemId, orderQty, due);
                }
            }
        }

        demandRows.saveAll(demands.values());
        planRows.saveAll(plans.values());
        run.recordTotals(demands.size(), plans.size(),
                (int) demands.values().stream().filter(MrpDemand::isShortage).count());
        runs.save(run);

        audit.record("mrp.run", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "mrp_run", String.valueOf(run.getId()),
                Map.of("runNo", run.getRunNo(),
                        "supply", String.valueOf(plans.size()),
                        "shortage", String.valueOf(run.getShortageCount())));

        return get(run.getId());
    }

    private Map<Long, Bucket> seedFromSales(LocalDate from, LocalDate to) {
        Map<Long, Bucket> buckets = new LinkedHashMap<>();
        for (OpenDemandLine line : sales.openDemand(from, to)) {
            Bucket bucket = buckets.computeIfAbsent(line.itemId(), id -> new Bucket(0));
            bucket.fromSales = bucket.fromSales.add(line.qty());
            bucket.requiredDate = earlier(bucket.requiredDate, line.requiredDate());
        }
        return buckets;
    }

    private Map<Long, BigDecimal> openSupplyByItem() {
        Map<Long, BigDecimal> byItem = new HashMap<>();
        for (com.genisource.erp.purchase.OpenSupplyLine line : purchase.openSupply()) {
            byItem.merge(line.itemId(), line.qty(), BigDecimal::add);
        }
        for (OpenSupplyLine line : production.openSupply()) {
            byItem.merge(line.itemId(), line.qty(), BigDecimal::add);
        }
        return byItem;
    }

    /**
     * Adds what the item's components need to the next level.
     *
     * <p>A component that already appeared at a shallower level keeps the level it was
     * first found at: the demand accumulates onto its existing row and its recommendation
     * is enlarged, but its own components are not exploded a second time. Re-exploding
     * would need a low-level code per item, which is the natural next step and is left
     * until a second customer actually hits it.
     */
    private void explodeInto(Map<Long, Bucket> buckets,
                             Map<Integer, List<Long>> itemsByLevel,
                             int level,
                             Long itemId,
                             BigDecimal qty,
                             LocalDate requiredDate) {
        List<Long> atLevel = itemsByLevel.computeIfAbsent(level, key -> new ArrayList<>());
        for (ComponentRequirement requirement : explosion.directComponents(itemId, qty)) {
            Bucket bucket = buckets.get(requirement.itemId());
            if (bucket == null) {
                bucket = new Bucket(level);
                buckets.put(requirement.itemId(), bucket);
            }
            bucket.fromComponents = bucket.fromComponents.add(requirement.qty());
            bucket.requiredDate = earlier(bucket.requiredDate, requiredDate);
            if (!atLevel.contains(requirement.itemId())) {
                atLevel.add(requirement.itemId());
            }
        }
    }

    private MrpSupplyType supplyTypeOf(Item item) {
        if (explosion.releasedBomOf(item.getId()).isPresent()) {
            return MrpSupplyType.PRODUCTION;
        }
        if (item.isPurchased()) {
            return MrpSupplyType.PURCHASE;
        }
        return null;
    }

    private BigDecimal orderMultipleOf(Item item, MrpSupplyType supplyType) {
        if (supplyType != MrpSupplyType.PURCHASE) {
            return BigDecimal.ONE;
        }
        BigDecimal minimum = orZero(item.getMinOrderQty());
        return minimum.signum() > 0 ? minimum : BigDecimal.ONE;
    }

    /** Rounds a shortfall up to whole multiples of what the supplier will sell. */
    private static BigDecimal roundUp(BigDecimal qty, BigDecimal multiple) {
        if (multiple == null || multiple.signum() <= 0 || multiple.compareTo(BigDecimal.ONE) == 0) {
            return qty.setScale(6, RoundingMode.CEILING);
        }
        return qty.divide(multiple, 0, RoundingMode.CEILING).multiply(multiple)
                .setScale(6, RoundingMode.HALF_UP);
    }

    // -------------------------------------------------------------- converting

    /** Turns a purchase recommendation into a real purchase order. */
    @Transactional
    public MrpDtos.PlanView convertToPurchaseOrder(Long planId, MrpDtos.ConvertRequest request) {
        MrpPlanSupply plan = requireOpenPlan(planId);
        if (plan.getSupplyType() != MrpSupplyType.PURCHASE) {
            throw ApiException.badRequest("Plan row " + planId
                    + " recommends making the item, not buying it");
        }
        Item item = requireItem(plan.getItemId());
        Long supplierId = request != null && request.supplierId() != null
                ? request.supplierId() : plan.getSupplierId();
        if (supplierId == null) {
            throw ApiException.badRequest("Item " + item.getCode()
                    + " has no default supplier, so name one on the request");
        }
        BigDecimal qty = request != null && request.qty() != null ? request.qty() : plan.getQty();
        LocalDate bizDate = request != null && request.bizDate() != null
                ? request.bizDate() : LocalDate.now();

        PurchaseDtos.DocumentView order = purchase.createPlannedOrder(new PlannedPurchase(
                supplierId, plan.getItemId(), qty,
                request == null ? null : request.unitPrice(),
                null, bizDate, plan.getDueDate(), String.valueOf(plan.getId()),
                firstNonBlank(request == null ? null : request.remark(),
                        "From MRP run " + runNoOf(plan))));

        plan.convert(CONVERTED_PURCHASE, String.valueOf(order.id()), order.docNo());
        planRows.save(plan);
        audit.record("mrp.convert.purchase", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "mrp_plan_supply", String.valueOf(planId),
                Map.of("order", order.docNo(), "item", item.getCode()));
        return planViews(List.of(plan)).get(0);
    }

    /** Turns a make recommendation into a works order, BOM and routing frozen onto it. */
    @Transactional
    public MrpDtos.PlanView convertToProductionOrder(Long planId, MrpDtos.ConvertRequest request) {
        MrpPlanSupply plan = requireOpenPlan(planId);
        if (plan.getSupplyType() != MrpSupplyType.PRODUCTION) {
            throw ApiException.badRequest("Plan row " + planId
                    + " recommends buying the item, not making it");
        }
        Item item = requireItem(plan.getItemId());
        BigDecimal qty = request != null && request.qty() != null ? request.qty() : plan.getQty();
        LocalDate due = plan.getDueDate() == null ? LocalDate.now() : plan.getDueDate();

        ProductionDtos.OrderView order = production.createPlannedOrder(new PlannedProductionOrder(
                plan.getItemId(), qty, due,
                request == null ? null : request.warehouseId(),
                String.valueOf(plan.getId()),
                firstNonBlank(request == null ? null : request.remark(),
                        "From MRP run " + runNoOf(plan))));

        plan.convert(CONVERTED_PRODUCTION, String.valueOf(order.id()), order.docNo());
        planRows.save(plan);
        audit.record("mrp.convert.production", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "mrp_plan_supply", String.valueOf(planId),
                Map.of("order", order.docNo(), "item", item.getCode()));
        return planViews(List.of(plan)).get(0);
    }

    /** Drops a recommendation, recording why. */
    @Transactional
    public MrpDtos.PlanView ignore(Long planId, String reason) {
        MrpPlanSupply plan = requireOpenPlan(planId);
        plan.ignore(blankToNull(reason));
        planRows.save(plan);
        audit.record("mrp.ignore", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "mrp_plan_supply", String.valueOf(planId),
                Map.of("item", String.valueOf(plan.getItemId())));
        return planViews(List.of(plan)).get(0);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * One item's accumulated demand before it is netted.
     *
     * <p>Mutable on purpose: an item can be asked for by a sales order and, separately, by
     * the sub-assembly that contains it. The second contribution is added to the same
     * bucket rather than creating a second row, because the plan has one recommendation
     * per item per run.
     */
    private static final class Bucket {
        private BigDecimal fromSales = BigDecimal.ZERO;
        private BigDecimal fromComponents = BigDecimal.ZERO;
        private LocalDate requiredDate;
        private final int level;

        private Bucket(int level) {
            this.level = level;
        }
    }

    private MrpRun requireRun(Long id) {
        return runs.findById(id)
                .orElseThrow(() -> ApiException.notFound("MRP run " + id + " does not exist"));
    }

    private MrpPlanSupply requireOpenPlan(Long id) {
        MrpPlanSupply plan = planRows.findById(id)
                .orElseThrow(() -> ApiException.notFound("Plan row " + id + " does not exist"));
        if (!plan.getStatus().isOpen()) {
            throw ApiException.invalidState("Plan row " + id + " is already "
                    + plan.getStatus().name());
        }
        return plan;
    }

    private Item requireItem(Long id) {
        return items.findById(id)
                .orElseThrow(() -> ApiException.notFound("Item " + id + " does not exist"));
    }

    private String runNoOf(MrpPlanSupply plan) {
        return runs.findById(plan.getRunId()).map(MrpRun::getRunNo).orElse("?");
    }

    private MrpDtos.RunView runView(MrpRun run) {
        return new MrpDtos.RunView(
                run.getId(), run.getRunNo(), run.getStatus(), run.getHorizonDays(),
                run.getDemandFrom(), run.getDemandTo(), run.isIncludeSafety(),
                run.getMaxLevels(), run.getItemCount(), run.getSupplyCount(),
                run.getShortageCount(), run.getRunBy(), run.getCreatedAt(), run.getRemark());
    }

    private List<MrpDtos.DemandView> demandViews(List<MrpDemand> source) {
        Map<Long, Item> itemById = items.findAllById(source.stream()
                        .map(MrpDemand::getItemId).distinct().toList())
                .stream().collect(Collectors.toMap(Item::getId, Function.identity()));
        return source.stream().map(row -> {
            Item item = itemById.get(row.getItemId());
            return new MrpDtos.DemandView(
                    row.getId(), row.getItemId(),
                    item == null ? null : item.getCode(),
                    item == null ? null : item.getName(),
                    item == null ? null : item.getUomCode(),
                    row.getItemLevel(), row.getGrossQty(), row.getOnHand(), row.getReservedQty(),
                    row.getLockedQty(), row.getInTransit(), row.getSafetyStock(), row.getNetQty(),
                    row.getRequiredDate(), row.getFromSales(), row.getFromComponents(),
                    row.isShortage());
        }).toList();
    }

    private List<MrpDtos.PlanView> planViews(List<MrpPlanSupply> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        Map<Long, Item> itemById = items.findAllById(source.stream()
                        .map(MrpPlanSupply::getItemId).distinct().toList())
                .stream().collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<Long, Partner> partnerById = partners.findAllById(source.stream()
                        .map(MrpPlanSupply::getSupplierId).filter(Objects::nonNull)
                        .distinct().toList())
                .stream().collect(Collectors.toMap(Partner::getId, Function.identity()));
        return source.stream().map(row -> {
            Item item = itemById.get(row.getItemId());
            Partner supplier = partnerById.get(row.getSupplierId());
            return new MrpDtos.PlanView(
                    row.getId(), row.getItemId(),
                    item == null ? null : item.getCode(),
                    item == null ? null : item.getName(),
                    item == null ? null : item.getUomCode(),
                    row.getSupplyType(), row.getQty(), row.getNetQty(), row.getOrderMultiple(),
                    row.getSupplierId(), supplier == null ? null : supplier.getName(),
                    row.getLeadTimeDays(), row.getStartDate(), row.getDueDate(), row.getStatus(),
                    row.getConvertedType(), row.getConvertedId(), row.getConvertedNo(),
                    row.getRemark());
        }).toList();
    }

    private static LocalDate earlier(LocalDate left, LocalDate right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first.trim() : second;
    }
}
