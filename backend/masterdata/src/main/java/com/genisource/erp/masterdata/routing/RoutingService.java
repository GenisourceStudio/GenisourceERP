package com.genisource.erp.masterdata.routing;

import com.genisource.erp.masterdata.item.Item;
import com.genisource.erp.masterdata.item.ItemRepository;
import com.genisource.erp.platform.common.Codes;
import com.genisource.erp.platform.common.RevisionStatus;
import com.genisource.erp.masterdata.workcenter.WorkCenter;
import com.genisource.erp.masterdata.workcenter.WorkCenterRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routing use cases.
 *
 * <p>Same revision rules as BOMs: drafts are editable, exactly one released revision per
 * item, released revisions are immutable. Operation sequence numbers are unique and
 * auto-assigned in tens so a step can always be inserted between two existing ones.
 */
@Service
@Transactional(readOnly = true)
public class RoutingService {

    private static final int SEQ_STEP = 10;

    private final RoutingRepository routings;
    private final ItemRepository items;
    private final WorkCenterRepository workCenters;
    private final AuditService audit;

    public RoutingService(RoutingRepository routings,
                          ItemRepository items,
                          WorkCenterRepository workCenters,
                          AuditService audit) {
        this.routings = routings;
        this.items = items;
        this.workCenters = workCenters;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ read

    public PageResponse<RoutingDtos.RoutingSummary> search(String keyword,
                                                           RevisionStatus status,
                                                           Long itemId,
                                                           int page,
                                                           int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 200),
                Sort.by("itemId").ascending().and(Sort.by("version").ascending()));
        Page<Routing> result = routings.findAll(specification(keyword, status, itemId), pageable);
        return PageResponse.of(result, summaries(result.getContent()));
    }

    public RoutingDtos.RoutingDetail get(Long id) {
        return detail(require(id));
    }

    public List<RoutingDtos.RoutingSummary> versionsOf(Long itemId) {
        return summaries(routings.findByItemIdOrderByVersionAsc(itemId)).stream()
                .sorted(Comparator.comparing(RoutingDtos.RoutingSummary::version).reversed())
                .toList();
    }

    // ----------------------------------------------------------------- write

    @Transactional
    public RoutingDtos.RoutingDetail create(RoutingDtos.RoutingRequest request) {
        Item item = requireItem(request.itemId());
        String version = requireVersion(request.version());
        if (routings.existsByItemIdAndVersion(item.getId(), version)) {
            throw ApiException.duplicateCode("Routing version " + version + " already exists for this item");
        }
        Routing routing = new Routing(item.getId(), version);
        routing.setRemark(request.remark());
        routing.tidy();
        applyOperations(routing, request.operations());
        routings.save(routing);
        audit("routing.create", routing, Map.of("itemId", item.getId(), "version", version));
        return detail(routing);
    }

    @Transactional
    public RoutingDtos.RoutingDetail update(Long id, RoutingDtos.RoutingRequest request) {
        Routing routing = require(id);
        routing.ensureEditable();
        if (!routing.getItemId().equals(request.itemId())) {
            throw ApiException.badRequest("A routing cannot be moved to another item; copy it instead");
        }
        String version = requireVersion(request.version());
        if (!version.equals(routing.getVersion())
                && routings.existsByItemIdAndVersion(routing.getItemId(), version)) {
            throw ApiException.duplicateCode("Routing version " + version + " already exists for this item");
        }
        routing.setVersion(version);
        routing.setRemark(request.remark());
        routing.tidy();
        applyOperations(routing, request.operations());
        routings.save(routing);
        audit("routing.update", routing, Map.of("version", version));
        return detail(routing);
    }

    @Transactional
    public RoutingDtos.RoutingDetail release(Long id) {
        Routing routing = require(id);
        if (routing.getStatus() != RevisionStatus.DRAFT) {
            throw ApiException.invalidState(
                    "Only a draft can be released; this routing is " + routing.getStatus());
        }
        if (routing.getOperations().isEmpty()) {
            throw ApiException.invalidState("A routing needs at least one operation before release");
        }
        for (Routing previous : routings.findByItemIdAndStatus(routing.getItemId(), RevisionStatus.RELEASED)) {
            if (!previous.getId().equals(routing.getId())) {
                previous.obsolete();
                routings.save(previous);
            }
        }
        routing.release();
        routings.save(routing);
        audit("routing.release", routing, Map.of("version", routing.getVersion()));
        return detail(routing);
    }

    @Transactional
    public RoutingDtos.RoutingDetail obsolete(Long id) {
        Routing routing = require(id);
        if (routing.getStatus() != RevisionStatus.RELEASED) {
            throw ApiException.invalidState(
                    "Only a released routing can be obsoleted; this one is " + routing.getStatus());
        }
        routing.obsolete();
        routings.save(routing);
        audit("routing.obsolete", routing, Map.of("version", routing.getVersion()));
        return detail(routing);
    }

    @Transactional
    public RoutingDtos.RoutingDetail copy(Long id, String newVersion) {
        Routing source = require(id);
        String version = requireVersion(newVersion);
        if (routings.existsByItemIdAndVersion(source.getItemId(), version)) {
            throw ApiException.duplicateCode("Routing version " + version + " already exists for this item");
        }
        Routing copy = new Routing(source.getItemId(), version);
        copy.setRemark(source.getRemark());
        List<RoutingOperation> operations = new ArrayList<>();
        for (RoutingOperation operation : source.getOperations()) {
            RoutingOperation cloned = new RoutingOperation(
                    operation.getSeq(), operation.getWorkCenterId(), operation.getName());
            cloned.setSetupMinutes(operation.getSetupMinutes());
            cloned.setRunMinutesPerUnit(operation.getRunMinutesPerUnit());
            cloned.setRemark(operation.getRemark());
            cloned.tidy();
            operations.add(cloned);
        }
        copy.replaceOperations(operations);
        routings.save(copy);
        audit("routing.copy", copy, Map.of("from", source.getVersion(), "to", version));
        return detail(copy);
    }

    @Transactional
    public void delete(Long id) {
        Routing routing = require(id);
        if (routing.getStatus() != RevisionStatus.DRAFT) {
            throw ApiException.invalidState(
                    "Only a draft can be deleted; obsolete it instead so history survives");
        }
        routings.delete(routing);
        audit("routing.delete", routing, Map.of("version", routing.getVersion()));
    }

    // ------------------------------------------------------------- internals

    private Routing require(Long id) {
        return routings.findWithOperationsById(id)
                .orElseThrow(() -> ApiException.notFound("Routing " + id + " does not exist"));
    }

    private Item requireItem(Long itemId) {
        return items.findById(itemId)
                .orElseThrow(() -> ApiException.notFound("Item " + itemId + " does not exist"));
    }

    private String requireVersion(String raw) {
        String version = Codes.normalise(raw);
        if (version == null) {
            throw ApiException.badRequest("Routing version is required");
        }
        return version;
    }

    private Specification<Routing> specification(String keyword, RevisionStatus status, Long itemId) {
        Specification<Routing> spec = (root, query, cb) -> cb.conjunction();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (itemId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("itemId"), itemId));
        }
        if (keyword != null && !keyword.isBlank()) {
            String term = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
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

    private void applyOperations(Routing routing, List<RoutingDtos.OperationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            routing.replaceOperations(List.of());
            return;
        }
        Set<Integer> used = new HashSet<>();
        int nextAuto = SEQ_STEP;
        List<RoutingOperation> operations = new ArrayList<>();

        for (RoutingDtos.OperationRequest request : requests) {
            WorkCenter center = workCenters.findById(request.workCenterId())
                    .orElseThrow(() -> ApiException.badRequest(
                            "Work centre " + request.workCenterId() + " does not exist"));
            if (!center.isActive()) {
                throw ApiException.badRequest(
                        "Work centre " + center.getCode() + " is inactive");
            }

            int seq = request.seq() == null ? nextAuto : request.seq();
            while (!used.add(seq)) {
                if (request.seq() != null) {
                    throw ApiException.badRequest("Operation number " + seq + " is used more than once");
                }
                seq += SEQ_STEP;
            }
            nextAuto = Math.max(nextAuto, seq + SEQ_STEP);

            RoutingOperation operation = new RoutingOperation(seq, center.getId(), request.name().trim());
            operation.setSetupMinutes(request.setupMinutes());
            operation.setRunMinutesPerUnit(request.runMinutesPerUnit());
            operation.setRemark(request.remark());
            operation.tidy();
            operations.add(operation);
        }
        operations.sort(Comparator.comparingInt(RoutingOperation::getSeq));
        routing.replaceOperations(operations);
    }

    private List<RoutingDtos.RoutingSummary> summaries(List<Routing> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        Map<Long, Item> itemById = items.findByIdIn(source.stream().map(Routing::getItemId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        return source.stream().map(routing -> {
            Item item = itemById.get(routing.getItemId());
            return new RoutingDtos.RoutingSummary(
                    routing.getId(),
                    routing.getItemId(),
                    item == null ? null : item.getCode(),
                    item == null ? null : item.getName(),
                    routing.getVersion(),
                    routing.getStatus(),
                    routing.getOperations().size(),
                    routing.runMinutesPerUnit(),
                    routing.getReleasedAt(),
                    routing.getUpdatedAt(),
                    routing.getRemark());
        }).toList();
    }

    private RoutingDtos.RoutingDetail detail(Routing routing) {
        Item item = items.findById(routing.getItemId()).orElse(null);
        List<Long> workCenterIds = routing.getOperations().stream()
                .map(RoutingOperation::getWorkCenterId)
                .distinct()
                .toList();
        Map<Long, WorkCenter> centers = workCenterIds.isEmpty() ? Map.of()
                : workCenters.findAllById(workCenterIds).stream()
                        .collect(Collectors.toMap(WorkCenter::getId, Function.identity()));

        List<RoutingDtos.OperationView> operations = routing.getOperations().stream()
                .map(operation -> {
                    WorkCenter center = centers.get(operation.getWorkCenterId());
                    return new RoutingDtos.OperationView(
                            operation.getId(),
                            operation.getSeq(),
                            operation.getWorkCenterId(),
                            center == null ? null : center.getCode(),
                            center == null ? null : center.getName(),
                            operation.getName(),
                            operation.getSetupMinutes(),
                            operation.getRunMinutesPerUnit(),
                            operation.getRemark());
                })
                .toList();

        BigDecimal total = routing.getOperations().stream()
                .map(RoutingOperation::getRunMinutesPerUnit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new RoutingDtos.RoutingDetail(
                routing.getId(),
                routing.getItemId(),
                item == null ? null : item.getCode(),
                item == null ? null : item.getName(),
                item == null ? null : item.getUomCode(),
                routing.getVersion(),
                routing.getStatus(),
                routing.getReleasedAt(),
                routing.getRemark(),
                routing.getRowVersion(),
                total,
                operations);
    }

    private void audit(String action, Routing routing, Map<String, Object> detail) {
        audit.record(action, CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "md_routing", String.valueOf(routing.getId()), detail);
    }
}
