package com.genisource.erp.masterdata.item;

import com.genisource.erp.masterdata.bom.BomRepository;
import com.genisource.erp.masterdata.category.ItemCategory;
import com.genisource.erp.masterdata.category.ItemCategoryRepository;
import com.genisource.erp.masterdata.partner.Partner;
import com.genisource.erp.masterdata.partner.PartnerRepository;
import com.genisource.erp.platform.common.Codes;
import com.genisource.erp.platform.common.RecordStatus;
import com.genisource.erp.platform.common.RevisionStatus;
import com.genisource.erp.masterdata.uom.Uom;
import com.genisource.erp.masterdata.uom.UomRepository;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.ItemUsageGuard;
import com.genisource.erp.platform.common.PageResponse;
import com.genisource.erp.platform.security.CurrentUser;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Item master use cases.
 *
 * <p>Three invariants are enforced here rather than in the database, because each needs a
 * message a factory clerk can act on:
 * <ol>
 *   <li>a code is unique per tenant and case-insensitive;</li>
 *   <li>facets must stay consistent — a service cannot be manufactured, an item that is
 *       not purchased cannot name a default supplier, a purchasing unit needs a factor;</li>
 *   <li>an item used as a component of a released BOM cannot be deactivated, otherwise
 *       planning would expand into a material nobody can buy;</li>
 *   <li>an item still held by another module cannot be deactivated either — the stock
 *       module contributes that check through {@link ItemUsageGuard}, so master data
 *       never has to know that stock exists.</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository items;
    private final ItemCategoryRepository categories;
    private final PartnerRepository partners;
    private final UomRepository uoms;
    private final BomRepository boms;
    private final List<ItemUsageGuard> usageGuards;
    private final AuditService audit;

    public ItemService(ItemRepository items,
                       ItemCategoryRepository categories,
                       PartnerRepository partners,
                       UomRepository uoms,
                       BomRepository boms,
                       List<ItemUsageGuard> usageGuards,
                       AuditService audit) {
        this.items = items;
        this.categories = categories;
        this.partners = partners;
        this.uoms = uoms;
        this.boms = boms;
        this.usageGuards = usageGuards;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ read

    public PageResponse<ItemDtos.ItemView> search(String keyword,
                                         ItemType itemType,
                                         RecordStatus status,
                                         Long categoryId,
                                         int page,
                                         int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by("code").ascending());
        Page<Item> result = items.findAll(specification(keyword, itemType, status, categoryId), pageable);
        return PageResponse.of(result, views(result.getContent()));
    }

    public ItemDtos.ItemView get(Long id) {
        Item item = require(id);
        return views(List.of(item)).get(0);
    }

    public List<ItemDtos.ItemOption> options(String keyword, int limit) {
        String term = "%" + (keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return items.searchTop(term, RecordStatus.ACTIVE, PageRequest.of(0, clampLimit(limit))).stream()
                .map(item -> new ItemDtos.ItemOption(
                        item.getId(), item.getCode(), item.getName(), item.getSpec(), item.getUomCode()))
                .toList();
    }

    // ----------------------------------------------------------------- write

    @Transactional
    public ItemDtos.ItemView create(ItemDtos.ItemRequest request) {
        String code = requireCode(request.code());
        if (items.existsByCode(code)) {
            throw ApiException.duplicateCode("Item code " + code + " already exists");
        }
        validate(request);

        Item item = new Item(code, request.name().trim(), request.itemType(), request.uomCode().trim());
        apply(item, request);
        items.save(item);
        audit.record("item.create", CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "md_item", String.valueOf(item.getId()),
                Map.of("code", item.getCode(), "name", item.getName()));
        return views(List.of(item)).get(0);
    }

    @Transactional
    public ItemDtos.ItemView update(Long id, ItemDtos.ItemRequest request) {
        Item item = require(id);
        String code = requireCode(request.code());
        if (!code.equals(item.getCode()) && items.existsByCode(code)) {
            throw ApiException.duplicateCode("Item code " + code + " already exists");
        }
        validate(request);

        item.setCode(code);
        item.setName(request.name().trim());
        item.setItemType(request.itemType());
        item.setUomCode(request.uomCode().trim());
        apply(item, request);
        items.save(item);
        audit.record("item.update", CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "md_item", String.valueOf(item.getId()),
                Map.of("code", item.getCode(), "name", item.getName()));
        return views(List.of(item)).get(0);
    }

    @Transactional
    public ItemDtos.ItemView changeStatus(Long id, RecordStatus target) {
        Item item = require(id);
        if (target == RecordStatus.INACTIVE && item.isActive()) {
            List<Long> parents = new java.util.ArrayList<>(
                    boms.findParentItemIdsUsingComponent(id, RevisionStatus.RELEASED));
            boms.findParentItemIdsUsingSubstitute(id, RevisionStatus.RELEASED)
                    .stream()
                    .filter(parentId -> !parents.contains(parentId))
                    .forEach(parents::add);
            if (!parents.isEmpty()) {
                throw ApiException.inUse(
                        "Item " + item.getCode() + " is used by released BOM(s) of " + itemNames(parents)
                                + " as a component or substitute and cannot be deactivated");
            }
            for (ItemUsageGuard guard : usageGuards) {
                guard.assertCanDeactivate(id);
            }
        }
        item.setStatus(target);
        items.save(item);
        audit.record("item.status", CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "md_item", String.valueOf(item.getId()), Map.of("status", target.name()));
        return views(List.of(item)).get(0);
    }

    // ------------------------------------------------------------- internals

    Item require(Long id) {
        return items.findById(id)
                .orElseThrow(() -> ApiException.notFound("Item " + id + " does not exist"));
    }

    private Specification<Item> specification(String keyword,
                                              ItemType itemType,
                                              RecordStatus status,
                                              Long categoryId) {
        Specification<Item> spec = (root, query, cb) -> cb.conjunction();
        if (keyword != null && !keyword.isBlank()) {
            String term = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), term),
                    cb.like(cb.lower(root.get("name")), term),
                    cb.like(cb.lower(cb.coalesce(root.get("spec"), "")), term)));
        }
        if (itemType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("itemType"), itemType));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
        }
        return spec;
    }

    private String requireCode(String raw) {
        String code = Codes.normalise(raw);
        if (code == null) {
            throw ApiException.badRequest("Item code is required");
        }
        if (code.length() > 64) {
            throw ApiException.badRequest("Item code must be at most 64 characters");
        }
        return code;
    }

    /**
     * Rejects combinations that would confuse every downstream module. Each rule exists
     * because the alternative is a planning run that quietly produces nonsense.
     */
    private void validate(ItemDtos.ItemRequest request) {
        Uom uom = uoms.require(request.uomCode().trim());
        if (request.purchaseUomCode() != null && !request.purchaseUomCode().isBlank()) {
            uoms.require(request.purchaseUomCode().trim());
        }

        if (request.categoryId() != null
                && !categories.existsById(request.categoryId())) {
            throw ApiException.badRequest("Item category " + request.categoryId() + " does not exist");
        }

        if (request.itemType() == ItemType.SERVICE) {
            if (request.manufactured()) {
                throw ApiException.badRequest("A service cannot be manufactured");
            }
            if (request.lotControlled() || request.serialControlled()) {
                throw ApiException.badRequest("A service cannot be lot or serial controlled");
            }
        }

        if (!request.purchased() && request.defaultSupplierId() != null) {
            throw ApiException.badRequest(
                    "A default supplier only makes sense for a purchased item");
        }

        if (request.defaultSupplierId() != null) {
            Partner supplier = partners.findById(request.defaultSupplierId())
                    .orElseThrow(() -> ApiException.badRequest(
                            "Supplier " + request.defaultSupplierId() + " does not exist"));
            if (!supplier.getPartnerType().tradesAsSupplier()) {
                throw ApiException.badRequest(
                        "Partner " + supplier.getCode() + " is not a supplier");
            }
        }

        BigDecimal factor = request.purchaseFactor();
        if (factor != null && factor.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Purchase factor must be greater than zero");
        }
        if (uom.getDecimals() == 0
                && request.purchaseUomCode() == null
                && factor != null
                && factor.stripTrailingZeros().scale() > 0) {
            throw ApiException.badRequest(
                    "Unit " + uom.getCode() + " does not allow fractional quantities");
        }
    }

    private void apply(Item item, ItemDtos.ItemRequest request) {
        item.setSpec(request.spec());
        item.setCategoryId(request.categoryId());
        item.setPurchaseUomCode(request.purchaseUomCode());
        item.setPurchaseFactor(request.purchaseFactor());
        item.setPurchased(request.purchased());
        item.setPurchaseLeadTimeDays(request.purchaseLeadTimeDays());
        item.setMinOrderQty(request.minOrderQty());
        item.setSafetyStock(request.safetyStock());
        item.setDefaultSupplierId(request.defaultSupplierId());
        item.setSold(request.sold());
        item.setSalePrice(request.salePrice());
        item.setManufactured(request.manufactured());
        item.setLotControlled(request.lotControlled());
        item.setSerialControlled(request.serialControlled());
        item.setShelfLifeDays(request.shelfLifeDays());
        item.setStandardCost(request.standardCost());
        item.setCurrency(request.currency());
        item.setTaxRate(request.taxRate());
        item.setRemark(request.remark());
        if (request.status() != null) {
            item.setStatus(request.status());
        }
        item.tidy();
    }

    List<ItemDtos.ItemView> views(List<Item> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        Map<Long, String> categoryNames = categories
                .findAllById(source.stream().map(Item::getCategoryId).filter(Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(ItemCategory::getId, ItemCategory::getName));
        Map<Long, String> supplierNames = partners
                .findAllById(source.stream().map(Item::getDefaultSupplierId).filter(Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Partner::getId, Partner::getName));
        Map<String, Uom> uomByCode = uoms.findAll().stream()
                .collect(Collectors.toMap(Uom::getCode, Function.identity()));

        return source.stream()
                .map(item -> toView(item, categoryNames, supplierNames, uomByCode))
                .toList();
    }

    private ItemDtos.ItemView toView(Item item,
                                     Map<Long, String> categoryNames,
                                     Map<Long, String> supplierNames,
                                     Map<String, Uom> uomByCode) {
        Uom uom = uomByCode.get(item.getUomCode());
        return new ItemDtos.ItemView(
                item.getId(),
                item.getCode(),
                item.getName(),
                item.getSpec(),
                item.getCategoryId(),
                categoryNames.get(item.getCategoryId()),
                item.getItemType(),
                item.getUomCode(),
                uom == null ? null : uom.getName(),
                item.getPurchaseUomCode(),
                item.getPurchaseFactor(),
                item.isPurchased(),
                item.getPurchaseLeadTimeDays(),
                item.getMinOrderQty(),
                item.getSafetyStock(),
                item.getDefaultSupplierId(),
                supplierNames.get(item.getDefaultSupplierId()),
                item.isSold(),
                item.getSalePrice(),
                item.isManufactured(),
                item.isLotControlled(),
                item.isSerialControlled(),
                item.getShelfLifeDays(),
                item.getStandardCost(),
                item.getCurrency(),
                item.getTaxRate(),
                item.getRemark(),
                item.getStatus(),
                item.getRowVersion(),
                item.getUpdatedAt());
    }

    private String itemNames(List<Long> ids) {
        Map<Long, String> byId = new LinkedHashMap<>();
        items.findByIdIn(ids).forEach(item -> byId.put(item.getId(), item.getCode()));
        List<String> codes = new ArrayList<>(byId.values());
        return String.join(", ", codes);
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 200);
    }

    private static int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), 50);
    }
}
