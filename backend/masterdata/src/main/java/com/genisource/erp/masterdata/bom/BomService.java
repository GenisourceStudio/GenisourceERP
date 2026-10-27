package com.genisource.erp.masterdata.bom;

import com.genisource.erp.masterdata.item.Item;
import com.genisource.erp.masterdata.item.ItemRepository;
import com.genisource.erp.platform.common.Codes;
import com.genisource.erp.platform.common.RevisionStatus;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bill-of-materials use cases.
 *
 * <p>Rules this service owns, because the database cannot express them:
 * <ul>
 *   <li>at most one {@code RELEASED} revision per item — releasing a newer one retires the
 *       previous, so "the current BOM" never has two answers;</li>
 *   <li>only drafts can change, so a released revision is stable under running orders;</li>
 *   <li>no cycles — a component must not reach back to its own parent through released
 *       revisions, which is the only graph MRP actually walks;</li>
 *   <li>line numbers are unique per BOM and auto-assigned in tens when omitted.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class BomService {

    private static final int LINE_STEP = 10;

    private final BomRepository boms;
    private final ItemRepository items;
    private final BomExplosion explosion;
    private final AuditService audit;

    public BomService(BomRepository boms,
                      ItemRepository items,
                      BomExplosion explosion,
                      AuditService audit) {
        this.boms = boms;
        this.items = items;
        this.explosion = explosion;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ read

    public PageResponse<BomDtos.BomSummary> search(String keyword,
                                                   RevisionStatus status,
                                                   Long itemId,
                                                   int page,
                                                   int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 200),
                Sort.by("itemId").ascending().and(Sort.by("version").ascending()));
        Page<Bom> result = boms.findAll(specification(keyword, status, itemId), pageable);
        return PageResponse.of(result, summaries(result.getContent()));
    }

    public BomDtos.BomDetail get(Long id) {
        Bom bom = require(id);
        return detail(bom);
    }

    public BomDtos.BomTreeNode tree(Long id, BigDecimal qty, int maxLevels) {
        return explosion.explode(id, qty, maxLevels);
    }

    /** Versions available for an item, newest first; used by the revision switcher. */
    public List<BomDtos.BomSummary> versionsOf(Long itemId) {
        return summaries(boms.findByItemIdOrderByVersionAsc(itemId)).stream()
                .sorted(Comparator.comparing(BomDtos.BomSummary::version).reversed())
                .toList();
    }

    // ----------------------------------------------------------------- write

    @Transactional
    public BomDtos.BomDetail create(BomDtos.BomRequest request) {
        Long itemId = requireItem(request.itemId()).getId();
        String version = requireVersion(request.version());
        if (boms.existsByItemIdAndVersion(itemId, version)) {
            throw ApiException.duplicateCode(
                    "BOM version " + version + " already exists for this item");
        }

        Bom bom = new Bom(request.itemId(), version,
                request.baseQty() == null ? BigDecimal.ONE : request.baseQty());
        bom.setEffectiveFrom(request.effectiveFrom());
        bom.setEffectiveTo(request.effectiveTo());
        bom.setRemark(request.remark());
        bom.tidy();

        applyLines(bom, request.lines());
        ensureNoCycle(bom);
        boms.save(bom);
        audit("bom.create", bom, Map.of("itemId", itemId, "version", version));
        return detail(bom);
    }

    @Transactional
    public BomDtos.BomDetail update(Long id, BomDtos.BomRequest request) {
        Bom bom = require(id);
        bom.ensureEditable();

        Long itemId = requireItem(request.itemId()).getId();
        String version = requireVersion(request.version());
        if ((!itemId.equals(bom.getItemId()) || !version.equals(bom.getVersion()))
                && boms.existsByItemIdAndVersion(itemId, version)) {
            throw ApiException.duplicateCode("BOM version " + version + " already exists for this item");
        }

        if (!itemId.equals(bom.getItemId())) {
            // Re-parenting a BOM would leave the old item without the revision the console
            // thinks it has; a copy is the honest operation, so refuse instead.
            throw ApiException.badRequest("A BOM cannot be moved to another item; copy it instead");
        }
        bom.setVersion(version);
        bom.setBaseQty(request.baseQty() == null ? bom.getBaseQty() : request.baseQty());
        bom.setEffectiveFrom(request.effectiveFrom());
        bom.setEffectiveTo(request.effectiveTo());
        bom.setRemark(request.remark());
        bom.tidy();

        applyLines(bom, request.lines());
        ensureNoCycle(bom);
        boms.save(bom);
        audit("bom.update", bom, Map.of("version", version));
        return detail(bom);
    }

    @Transactional
    public BomDtos.BomDetail release(Long id) {
        Bom bom = require(id);
        if (bom.getStatus() != RevisionStatus.DRAFT) {
            throw ApiException.invalidState(
                    "Only a draft can be released; this BOM is " + bom.getStatus());
        }
        if (bom.getLines().isEmpty()) {
            throw ApiException.invalidState("A BOM needs at least one component before release");
        }
        ensureComponentsUsable(bom);
        ensureNoCycle(bom);

        // Retire the previous revision of the same item: exactly one released BOM per item.
        for (Bom previous : boms.findByItemIdAndStatus(bom.getItemId(), RevisionStatus.RELEASED)) {
            if (!previous.getId().equals(bom.getId())) {
                previous.obsolete();
                boms.save(previous);
            }
        }
        bom.release();
        boms.save(bom);
        audit("bom.release", bom, Map.of("version", bom.getVersion()));
        return detail(bom);
    }

    @Transactional
    public BomDtos.BomDetail obsolete(Long id) {
        Bom bom = require(id);
        if (bom.getStatus() != RevisionStatus.RELEASED) {
            throw ApiException.invalidState(
                    "Only a released BOM can be obsoleted; this one is " + bom.getStatus());
        }
        bom.obsolete();
        boms.save(bom);
        audit("bom.obsolete", bom, Map.of("version", bom.getVersion()));
        return detail(bom);
    }

    @Transactional
    public BomDtos.BomDetail copy(Long id, String newVersion) {
        Bom source = require(id);
        String version = requireVersion(newVersion);
        if (boms.existsByItemIdAndVersion(source.getItemId(), version)) {
            throw ApiException.duplicateCode("BOM version " + version + " already exists for this item");
        }

        Bom copy = new Bom(source.getItemId(), version, source.getBaseQty());
        copy.setEffectiveFrom(source.getEffectiveFrom());
        copy.setEffectiveTo(source.getEffectiveTo());
        copy.setRemark(source.getRemark());

        List<BomLine> lines = new ArrayList<>();
        for (BomLine line : source.getLines()) {
            BomLine cloned = new BomLine(line.getLineNo(), line.getComponentItemId(), line.getQty());
            cloned.setLossRate(line.getLossRate());
            cloned.setPhantom(line.isPhantom());
            cloned.setRemark(line.getRemark());
            List<BomSubstitute> substitutes = new ArrayList<>();
            for (BomSubstitute substitute : line.getSubstitutes()) {
                substitutes.add(new BomSubstitute(substitute.getSubstituteItemId(), substitute.getPriority()));
            }
            cloned.replaceSubstitutes(substitutes);
            cloned.tidy();
            lines.add(cloned);
        }
        copy.replaceLines(lines);
        boms.save(copy);
        audit("bom.copy", copy, Map.of("from", source.getVersion(), "to", version));
        return detail(copy);
    }

    @Transactional
    public void delete(Long id) {
        Bom bom = require(id);
        if (bom.getStatus() != RevisionStatus.DRAFT) {
            throw ApiException.invalidState(
                    "Only a draft can be deleted; obsolete it instead so history survives");
        }
        boms.delete(bom);
        audit("bom.delete", bom, Map.of("version", bom.getVersion()));
    }

    // ------------------------------------------------------------- internals

    private Bom require(Long id) {
        return boms.findWithLinesById(id)
                .orElseThrow(() -> ApiException.notFound("BOM " + id + " does not exist"));
    }

    private Item requireItem(Long itemId) {
        return items.findById(itemId)
                .orElseThrow(() -> ApiException.notFound("Item " + itemId + " does not exist"));
    }

    private String requireVersion(String raw) {
        String version = Codes.normalise(raw);
        if (version == null) {
            throw ApiException.badRequest("BOM version is required");
        }
        return version;
    }

    private void applyLines(Bom bom, List<BomDtos.BomLineRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            bom.replaceLines(List.of());
            return;
        }
        Set<Integer> used = new HashSet<>();
        int nextAuto = LINE_STEP;
        List<BomLine> lines = new ArrayList<>();

        for (BomDtos.BomLineRequest request : requests) {
            Item component = requireItem(request.componentItemId());
            if (component.getId().equals(bom.getItemId())) {
                throw ApiException.bomCycle(
                        "Item " + component.getCode() + " cannot be a component of its own BOM");
            }
            if (request.qty() == null || request.qty().signum() <= 0) {
                throw ApiException.badRequest("Component quantity must be greater than zero");
            }

            int lineNo = request.lineNo() == null ? nextAuto : request.lineNo();
            while (!used.add(lineNo)) {
                if (request.lineNo() != null) {
                    throw ApiException.badRequest("Line number " + lineNo + " is used more than once");
                }
                lineNo += LINE_STEP;
            }
            nextAuto = Math.max(nextAuto, lineNo + LINE_STEP);

            BomLine line = new BomLine(lineNo, component.getId(), request.qty());
            line.setLossRate(request.lossRate());
            line.setPhantom(request.phantom());
            line.setRemark(request.remark());
            line.replaceSubstitutes(substitutesOf(request.substitutes(), component, bom.getItemId()));
            line.tidy();
            lines.add(line);
        }
        lines.sort(Comparator.comparingInt(BomLine::getLineNo));
        bom.replaceLines(lines);
    }

    private List<BomSubstitute> substitutesOf(List<BomDtos.BomSubstituteRequest> requests,
                                              Item primary,
                                              Long parentItemId) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        Set<Long> seen = new LinkedHashSet<>();
        List<BomSubstitute> substitutes = new ArrayList<>();
        int priority = 1;
        for (BomDtos.BomSubstituteRequest request : requests) {
            Item substitute = requireItem(request.itemId());
            if (substitute.getId().equals(primary.getId())) {
                throw ApiException.badRequest(
                        "Item " + substitute.getCode() + " cannot substitute itself");
            }
            if (substitute.getId().equals(parentItemId)) {
                // Substituting a component with the product being built is never meaningful
                // and would let a released BOM point back at its own parent item.
                throw ApiException.bomCycle(
                        "Item " + substitute.getCode() + " is the item this BOM builds "
                                + "and cannot substitute one of its own components");
            }
            if (!seen.add(substitute.getId())) {
                throw ApiException.badRequest(
                        "Item " + substitute.getCode() + " is listed twice as a substitute");
            }
            BomSubstitute entity = new BomSubstitute(substitute.getId(),
                    request.priority() == null ? priority : request.priority());
            entity.setRemark(request.remark());
            substitutes.add(entity);
            priority++;
        }
        return substitutes;
    }

    private void ensureComponentsUsable(Bom bom) {
        List<Long> ids = bom.getLines().stream().map(BomLine::getComponentItemId).toList();
        Map<Long, Item> byId = items.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        List<String> missing = ids.stream()
                .filter(id -> byId.containsKey(id) && !byId.get(id).isActive())
                .map(id -> byId.get(id).getCode())
                .toList();
        if (!missing.isEmpty()) {
            throw ApiException.invalidState(
                    "These components are inactive and cannot be released: " + String.join(", ", missing));
        }
    }

    /**
     * Rejects a revision whose components can already reach back to its own item.
     *
     * <p>Only released edges are walked, plus the lines being saved. That is precisely the
     * graph MRP expands, so a cycle buried in an obsolete revision does not block work —
     * and a cycle that would actually break planning is always caught.
     */
    private void ensureNoCycle(Bom bom) {
        Long itemId = bom.getItemId();
        Set<Long> visited = new HashSet<>();
        for (BomLine line : bom.getLines()) {
            Long componentId = line.getComponentItemId();
            if (componentId.equals(itemId)) {
                throw ApiException.bomCycle("A BOM cannot contain its own item as a component");
            }
            if (reaches(componentId, itemId, visited)) {
                Item component = items.findById(componentId).orElse(null);
                String code = component == null ? String.valueOf(componentId) : component.getCode();
                throw ApiException.bomCycle(
                        "Component " + code + " already contains this item somewhere below it, "
                                + "which would make the BOM circular");
            }
        }
    }

    private boolean reaches(Long from, Long target, Set<Long> visited) {
        if (from.equals(target)) {
            return true;
        }
        if (!visited.add(from)) {
            return false;
        }
        for (Long child : boms.findComponentItemIds(from, RevisionStatus.RELEASED)) {
            if (reaches(child, target, visited)) {
                return true;
            }
        }
        return false;
    }

    private Specification<Bom> specification(String keyword, RevisionStatus status, Long itemId) {
        Specification<Bom> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (itemId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("itemId"), itemId));
        }
        if (keyword != null && !keyword.isBlank()) {
            String term = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> {
                var subquery = query.subquery(Long.class);
                var item = subquery.from(Item.class);
                subquery.select(item.get("id"))
                        .where(cb.or(cb.like(cb.lower(item.get("code")), term),
                                cb.like(cb.lower(item.get("name")), term)));
                return root.get("itemId").in(subquery);
            });
        }
        return spec;
    }

    private List<BomDtos.BomSummary> summaries(List<Bom> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        Map<Long, Item> itemById = items.findByIdIn(
                        source.stream().map(Bom::getItemId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<Long, Integer> lineCounts = boms
                .countLinesByBomIds(source.stream().map(Bom::getId).toList())
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Long) row[1]).intValue()));

        return source.stream().map(bom -> {
            Item item = itemById.get(bom.getItemId());
            return new BomDtos.BomSummary(
                    bom.getId(),
                    bom.getItemId(),
                    item == null ? null : item.getCode(),
                    item == null ? null : item.getName(),
                    bom.getVersion(),
                    bom.getStatus(),
                    bom.getBaseQty(),
                    lineCounts.getOrDefault(bom.getId(), 0),
                    bom.getEffectiveFrom(),
                    bom.getEffectiveTo(),
                    bom.getReleasedAt(),
                    bom.getUpdatedAt(),
                    bom.getRemark());
        }).toList();
    }

    private BomDtos.BomDetail detail(Bom bom) {
        Item parent = items.findById(bom.getItemId()).orElse(null);
        Set<Long> componentIds = explosion.componentIds(bom);
        Map<Long, Item> components = componentIds.isEmpty() ? Map.of()
                : items.findByIdIn(componentIds).stream()
                        .collect(Collectors.toMap(Item::getId, Function.identity()));

        Set<Long> substituteIds = bom.getLines().stream()
                .flatMap(line -> line.getSubstitutes().stream())
                .map(BomSubstitute::getSubstituteItemId)
                .collect(Collectors.toSet());
        Map<Long, Item> substitutes = substituteIds.isEmpty() ? Map.of()
                : items.findByIdIn(substituteIds).stream()
                        .collect(Collectors.toMap(Item::getId, Function.identity()));

        List<BomDtos.BomLineView> lines = bom.getLines().stream()
                .map(line -> {
                    Item component = components.get(line.getComponentItemId());
                    List<BomDtos.BomSubstituteView> substituteViews = line.getSubstitutes().stream()
                            .map(substitute -> {
                                Item substituteItem = substitutes.get(substitute.getSubstituteItemId());
                                return new BomDtos.BomSubstituteView(
                                        substitute.getId(),
                                        substitute.getSubstituteItemId(),
                                        substituteItem == null ? null : substituteItem.getCode(),
                                        substituteItem == null ? null : substituteItem.getName(),
                                        substitute.getPriority(),
                                        substitute.getRemark());
                            })
                            .toList();
                    return new BomDtos.BomLineView(
                            line.getId(),
                            line.getLineNo(),
                            line.getComponentItemId(),
                            component == null ? null : component.getCode(),
                            component == null ? null : component.getName(),
                            component == null ? null : component.getSpec(),
                            component == null ? null : component.getUomCode(),
                            line.getQty(),
                            line.getLossRate(),
                            line.isPhantom(),
                            line.getRemark(),
                            substituteViews);
                })
                .toList();

        return new BomDtos.BomDetail(
                bom.getId(),
                bom.getItemId(),
                parent == null ? null : parent.getCode(),
                parent == null ? null : parent.getName(),
                parent == null ? null : parent.getUomCode(),
                bom.getVersion(),
                bom.getStatus(),
                bom.getBaseQty(),
                bom.getEffectiveFrom(),
                bom.getEffectiveTo(),
                bom.getReleasedAt(),
                bom.getRemark(),
                bom.getRowVersion(),
                lines);
    }

    private void audit(String action, Bom bom, Map<String, Object> detail) {
        audit.record(action, CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "md_bom", String.valueOf(bom.getId()), detail);
    }
}
