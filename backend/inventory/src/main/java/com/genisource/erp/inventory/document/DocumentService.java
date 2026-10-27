package com.genisource.erp.inventory.document;

import com.genisource.erp.inventory.lot.Lot;
import com.genisource.erp.inventory.lot.LotRepository;
import com.genisource.erp.inventory.lot.LotService;
import com.genisource.erp.inventory.movement.Movement;
import com.genisource.erp.inventory.movement.MovementRepository;
import com.genisource.erp.inventory.movement.MovementType;
import com.genisource.erp.inventory.stock.StockService;
import com.genisource.erp.inventory.stock.StockValuation;
import com.genisource.erp.inventory.warehouse.Warehouse;
import com.genisource.erp.inventory.warehouse.WarehouseService;
import com.genisource.erp.masterdata.item.Item;
import com.genisource.erp.masterdata.item.ItemRepository;
import com.genisource.erp.masterdata.partner.Partner;
import com.genisource.erp.masterdata.partner.PartnerRepository;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.numbering.DocumentNoAllocator;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.genisource.erp.inventory.warehouse.Location;
import com.genisource.erp.inventory.warehouse.LocationRepository;
import com.genisource.erp.inventory.warehouse.LocationService;
import com.genisource.erp.inventory.warehouse.WarehouseRepository;

/**
 * Stock documents: create, post, reverse.
 *
 * <p>The whole point of this class is the posting routine. Everything else — editing a
 * draft, listing documents, rendering one — is bookkeeping around it.
 *
 * <p><strong>Posting</strong> moves stock. In one transaction it writes the ledger
 * entries and updates the balances, so the two can never disagree. A document that
 * fails halfway leaves nothing behind.
 *
 * <p><strong>Reversing</strong> undoes a posting without touching it. The original
 * document stays on the books and a red-ink document appears beside it, carrying the
 * mirrored movements. This is the rule Chinese accounting follows, and it is the only
 * one that keeps the history readable: an auditor wants to see the mistake and the
 * correction, not a gap where both used to be.
 */
@Service
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documents;
    private final MovementRepository movements;
    private final StockService stock;
    private final LotService lots;
    private final LocationService locations;
    private final WarehouseService warehouses;
    private final ItemRepository items;
    private final PartnerRepository partners;
    private final LotRepository lotRepository;
    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;
    private final DocumentNoAllocator numbers;
    private final AuditService audit;

    public DocumentService(DocumentRepository documents,
                           MovementRepository movements,
                           StockService stock,
                           LotService lots,
                           LocationService locations,
                           WarehouseService warehouses,
                           ItemRepository items,
                           PartnerRepository partners,
                           LotRepository lotRepository,
                           LocationRepository locationRepository,
                           WarehouseRepository warehouseRepository,
                           DocumentNoAllocator numbers,
                           AuditService audit) {
        this.documents = documents;
        this.movements = movements;
        this.stock = stock;
        this.lots = lots;
        this.locations = locations;
        this.warehouses = warehouses;
        this.items = items;
        this.partners = partners;
        this.lotRepository = lotRepository;
        this.locationRepository = locationRepository;
        this.warehouseRepository = warehouseRepository;
        this.numbers = numbers;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ read

    public PageResponse<DocumentDtos.DocumentView> search(String keyword,
                                                           DocumentType docType,
                                                           DocumentStatus status,
                                                           Long warehouseId,
                                                           int page,
                                                           int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by("id").descending());
        Page<StockDocument> result = documents.findAll(
                specification(keyword, docType, status, warehouseId), pageable);
        return PageResponse.of(result, views(result.getContent()));
    }

    public DocumentDtos.DocumentView get(Long id) {
        return view(require(id));
    }

    // ----------------------------------------------------------------- write

    @Transactional
    public DocumentDtos.DocumentView create(DocumentDtos.DocumentRequest request) {
        StockDocument document = build(request, null, null);
        audit.record("inventory.document.create", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null),
                "inv_document", String.valueOf(document.getId()),
                Map.of("docNo", document.getDocNo(), "type", request.docType().name()));
        return view(document);
    }

    /**
     * Creates and posts a stock document on behalf of another module.
     *
     * <p>A purchase receipt and a sales shipment do not ask anybody to type a stock
     * document: they bring one with them. This is the door they use, and it posts in
     * the caller's transaction, so a purchase receipt that cannot move stock is not
     * posted either — the two are never half done.
     *
     * <p>The source is stamped on every ledger entry the posting writes, which is what
     * lets a movement say "I came from purchase receipt SL-20261028-0001" instead of
     * leaving the reader to guess.
     *
     * @param sourceType what kind of document caused this, e.g. {@code PUR_RECEIPT}
     * @param sourceId   its document number
     * @return the id of the inventory document that was posted
     */
    @Transactional
    public Long postSourced(DocumentDtos.DocumentRequest request, String sourceType, String sourceId) {
        StockDocument document = build(request, sourceType, sourceId);
        apply(document);
        document.post(Instant.now(), CurrentUser.username().orElse(null));
        documents.save(document);
        audit.record("inventory.document.post", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null),
                "inv_document", String.valueOf(document.getId()),
                Map.of("docNo", document.getDocNo(), "type", document.getDocType().name(),
                        "source", sourceId));
        return document.getId();
    }

    /** Shared by {@link #create} and {@link #postSourced}: validate and persist a draft. */
    private StockDocument build(DocumentDtos.DocumentRequest request,
                                String sourceType,
                                String sourceId) {
        Warehouse warehouse = warehouses.requireUsable(request.warehouseId());
        if (request.locationId() != null) {
            locations.requireUsable(request.locationId(), warehouse.getId());
        }
        Warehouse destination = null;
        if (request.docType().needsDestination()) {
            if (request.toWarehouseId() == null) {
                throw ApiException.badRequest("A transfer needs a destination warehouse");
            }
            destination = warehouses.requireUsable(request.toWarehouseId());
            if (destination.getId().equals(warehouse.getId())) {
                throw ApiException.badRequest("A transfer cannot start and end in the same warehouse");
            }
            if (request.toLocationId() != null) {
                locations.requireUsable(request.toLocationId(), destination.getId());
            }
        }
        validatePartner(request);

        StockDocument document = new StockDocument(
                numbers.next(request.docType().prefix(), request.bizDate()),
                request.docType(),
                warehouse.getId(),
                request.bizDate());
        document.setLocationId(request.locationId());
        document.setToWarehouseId(request.toWarehouseId());
        document.setToLocationId(request.toLocationId());
        document.setPartnerId(request.partnerId());
        document.setRemark(blankToNull(request.remark()));
        document.setSourceType(sourceType);
        document.setSourceId(sourceId);
        document.replaceLines(buildLines(request.lines()));
        documents.save(document);
        return document;
    }

    @Transactional
    public DocumentDtos.DocumentView update(Long id, DocumentDtos.DocumentRequest request) {
        StockDocument document = require(id);
        if (!document.getStatus().isEditable()) {
            throw ApiException.invalidState("Document " + document.getDocNo()
                    + " is already posted and cannot be edited");
        }
        Warehouse warehouse = warehouses.requireUsable(request.warehouseId());
        if (request.locationId() != null) {
            locations.requireUsable(request.locationId(), warehouse.getId());
        }
        if (request.docType().needsDestination()) {
            if (request.toWarehouseId() == null) {
                throw ApiException.badRequest("A transfer needs a destination warehouse");
            }
            Warehouse destination = warehouses.requireUsable(request.toWarehouseId());
            if (destination.getId().equals(warehouse.getId())) {
                throw ApiException.badRequest("A transfer cannot start and end in the same warehouse");
            }
            if (request.toLocationId() != null) {
                locations.requireUsable(request.toLocationId(), destination.getId());
            }
        }
        validatePartner(request);

        document.setDocType(request.docType());
        document.setWarehouseId(warehouse.getId());
        document.setLocationId(request.locationId());
        document.setToWarehouseId(request.toWarehouseId());
        document.setToLocationId(request.toLocationId());
        document.setPartnerId(request.partnerId());
        document.setBizDate(request.bizDate());
        document.setRemark(blankToNull(request.remark()));
        document.replaceLines(buildLines(request.lines()));
        documents.save(document);

        audit.record("inventory.document.update", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null),
                "inv_document", String.valueOf(document.getId()),
                Map.of("docNo", document.getDocNo()));
        return view(document);
    }

    /**
     * Posts the document: the ledger entries and the balances change together.
     *
     * <p>The four document types differ only in what a line means:
     * <ul>
     *   <li>a receipt adds stock at the cost on the line;</li>
     *   <li>an issue removes stock at the current average;</li>
     *   <li>a transfer removes it from one place and adds it to another at the same
     *       value, so a transfer never changes the total worth of the stock;</li>
     *   <li>a count compares the shelf with the book and posts the difference.</li>
     * </ul>
     */
    @Transactional
    public DocumentDtos.DocumentView post(Long id) {
        StockDocument document = require(id);
        apply(document);
        document.post(Instant.now(), CurrentUser.username().orElse(null));
        documents.save(document);

        audit.record("inventory.document.post", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null),
                "inv_document", String.valueOf(document.getId()),
                Map.of("docNo", document.getDocNo(), "type", document.getDocType().name()));
        return view(document);
    }

    /** The part of posting that writes stock: one ledger entry and one balance per line. */
    private void apply(StockDocument document) {
        if (!document.getStatus().isEditable()) {
            throw ApiException.invalidState("Document " + document.getDocNo()
                    + " is already posted and cannot be posted again");
        }
        if (document.getLines().isEmpty()) {
            throw ApiException.badRequest("Document " + document.getDocNo() + " has no lines");
        }
        for (DocumentLine line : document.getLines()) {
            Item item = requireItem(line.getItemId());
            Long lotId = resolveLot(item, line);
            switch (document.getDocType()) {
                case INBOUND -> postReceipt(document, line, item, lotId);
                case OUTBOUND -> postIssue(document, line, item, lotId);
                case TRANSFER -> postTransfer(document, line, item, lotId);
                case COUNT -> postCount(document, line, item, lotId);
            }
        }
    }

    /**
     * Undoes a posted document by writing its mirror image.
     *
     * <p>The mirror replays the original movements backwards, value included: the
     * reversal restores the exact amount the original carried, not an amount computed
     * from today's average.
     */
    @Transactional
    public DocumentDtos.DocumentView reverse(Long id) {
        StockDocument original = require(id);
        if (original.getStatus() != DocumentStatus.POSTED) {
            throw ApiException.invalidState("Only a posted document can be reversed");
        }
        if (original.getReversalOf() != null) {
            throw ApiException.invalidState("A reversal cannot be reversed; post a new document instead");
        }
        List<Movement> originalMovements = movements.findByDocumentIdOrderByIdAsc(id);
        if (originalMovements.isEmpty()) {
            throw ApiException.invalidState("Document " + original.getDocNo() + " has no ledger entries");
        }

        StockDocument reversal = new StockDocument(
                numbers.next(original.getDocType().prefix(), LocalDate.now()),
                original.getDocType(),
                original.getWarehouseId(),
                LocalDate.now());
        reversal.setLocationId(original.getLocationId());
        reversal.setToWarehouseId(original.getToWarehouseId());
        reversal.setToLocationId(original.getToLocationId());
        reversal.setPartnerId(original.getPartnerId());
        reversal.setRemark("Reversal of " + original.getDocNo());
        reversal.setReversalOf(original.getId());
        reversal.replaceLines(buildLines(original.getLines().stream()
                .map(line -> new DocumentDtos.LineRequest(
                        line.getItemId(), line.getLotId(), line.getQty(),
                        line.getUnitCost(), line.getCountedQty(), line.getRemark()))
                .toList()));
        documents.save(reversal);

        for (Movement source : originalMovements) {
            Item item = requireItem(source.getItemId());
            boolean wasInbound = source.getMovementType().isInbound();
            StockValuation.Result result = stock.adjust(
                    source.getWarehouseId(),
                    source.getLocationId(),
                    source.getItemId(),
                    source.getLotId(),
                    wasInbound ? source.getQty().negate() : source.getQty(),
                    wasInbound ? source.getAmount().negate() : source.getAmount(),
                    item.getCode());
            record(reversal, null, source.getMovementType().opposite(),
                    item, source.getLotId(), source.getWarehouseId(), source.getLocationId(),
                    source.getQty(), source.getUnitCost(), source.getAmount(),
                    result.qty(), result.totalAmount(), source.getRemark());
        }

        reversal.post(Instant.now(), CurrentUser.username().orElse(null));
        documents.save(reversal);
        original.markReversed();
        documents.save(original);

        audit.record("inventory.document.reverse", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null),
                "inv_document", String.valueOf(original.getId()),
                Map.of("docNo", original.getDocNo(), "reversal", reversal.getDocNo()));
        return view(reversal);
    }

    /**
     * Undoes everything one business document brought about.
     *
     * <p>A purchase receipt or a sales shipment may have produced several stock
     * documents, and reversing the business document has to undo all of them. Called
     * from the module that owns the business document, in its transaction, so the
     * business document and the stock it moved are never left disagreeing.
     *
     * @return the reversals that were written
     */
    @Transactional
    public List<DocumentDtos.DocumentView> reverseSourced(String sourceType, String sourceId) {
        return documents.findBySourceTypeAndSourceIdOrderByIdAsc(sourceType, sourceId)
                .stream()
                .map(source -> reverse(source.getId()))
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        StockDocument document = require(id);
        if (!document.getStatus().isEditable()) {
            throw ApiException.invalidState("Document " + document.getDocNo()
                    + " is already posted and cannot be deleted");
        }
        documents.delete(document);
        audit.record("inventory.document.delete", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null),
                "inv_document", String.valueOf(document.getId()),
                Map.of("docNo", document.getDocNo()));
    }

    // -------------------------------------------------------------- postings

    private void postReceipt(StockDocument document, DocumentLine line, Item item, Long lotId) {
        if (line.getQty().signum() <= 0) {
            throw ApiException.badRequest("A receipt line needs a quantity greater than zero");
        }
        BigDecimal unitCost = line.getUnitCost() == null ? BigDecimal.ZERO : line.getUnitCost();
        StockValuation.Result result = stock.receive(
                document.getWarehouseId(), document.getLocationId(), item.getId(), lotId,
                line.getQty(), unitCost);
        line.setUnitCost(result.avgCost());
        record(document, line, MovementType.RECEIPT, item, lotId,
                document.getWarehouseId(), document.getLocationId(),
                line.getQty(), unitCost, result.movementAmount(),
                result.qty(), result.totalAmount(), line.getRemark());
    }

    private void postIssue(StockDocument document, DocumentLine line, Item item, Long lotId) {
        if (line.getQty().signum() <= 0) {
            throw ApiException.badRequest("An issue line needs a quantity greater than zero");
        }
        StockValuation.Result result = stock.issue(
                document.getWarehouseId(), document.getLocationId(), item.getId(), lotId,
                line.getQty(), item.getCode());
        line.setUnitCost(result.avgCost());
        record(document, line, MovementType.ISSUE, item, lotId,
                document.getWarehouseId(), document.getLocationId(),
                line.getQty(), result.avgCost(), result.movementAmount(),
                result.qty(), result.totalAmount(), line.getRemark());
    }

    /**
     * A transfer is an issue plus a receipt at the same value.
     *
     * <p>Valuing the arrival at the source's average is what keeps the worth of the
     * stock unchanged: the goods leave at their price and arrive at that price.
     */
    private void postTransfer(StockDocument document, DocumentLine line, Item item, Long lotId) {
        if (line.getQty().signum() <= 0) {
            throw ApiException.badRequest("A transfer line needs a quantity greater than zero");
        }
        StockValuation.Result out = stock.issue(
                document.getWarehouseId(), document.getLocationId(), item.getId(), lotId,
                line.getQty(), item.getCode());
        line.setUnitCost(out.avgCost());
        record(document, line, MovementType.TRANSFER_OUT, item, lotId,
                document.getWarehouseId(), document.getLocationId(),
                line.getQty(), out.avgCost(), out.movementAmount(),
                out.qty(), out.totalAmount(), line.getRemark());

        StockValuation.Result in = stock.receive(
                document.getToWarehouseId(), document.getToLocationId(), item.getId(), lotId,
                line.getQty(), out.avgCost());
        record(document, line, MovementType.TRANSFER_IN, item, lotId,
                document.getToWarehouseId(), document.getToLocationId(),
                line.getQty(), out.avgCost(), out.movementAmount(),
                in.qty(), in.totalAmount(), line.getRemark());
    }

    /**
     * A count posts the difference between the shelf and the book.
     *
     * <p>Only the difference moves. A line that matches the book produces no ledger
     * entry at all — writing a zero movement would bury the real adjustments in noise.
     */
    private void postCount(StockDocument document, DocumentLine line, Item item, Long lotId) {
        if (line.getCountedQty() == null) {
            // The clerk did not reach this bin; nothing to adjust.
            return;
        }
        BigDecimal book = stock.currentQuantity(
                document.getWarehouseId(), document.getLocationId(), item.getId(), lotId);
        BigDecimal difference = line.getCountedQty().subtract(book);
        if (difference.signum() == 0) {
            line.setUnitCost(stock.currentAverage(
                    document.getWarehouseId(), document.getLocationId(), item.getId(), lotId));
            return;
        }
        if (difference.signum() > 0) {
            BigDecimal unitCost = stock.currentAverage(
                    document.getWarehouseId(), document.getLocationId(), item.getId(), lotId);
            if (unitCost.signum() == 0) {
                unitCost = item.getStandardCost() == null ? BigDecimal.ZERO : item.getStandardCost();
            }
            StockValuation.Result result = stock.receive(
                    document.getWarehouseId(), document.getLocationId(), item.getId(), lotId,
                    difference, unitCost);
            line.setUnitCost(unitCost);
            record(document, line, MovementType.COUNT_GAIN, item, lotId,
                    document.getWarehouseId(), document.getLocationId(),
                    difference, unitCost, result.movementAmount(),
                    result.qty(), result.totalAmount(), line.getRemark());
        } else {
            BigDecimal shortage = difference.negate();
            StockValuation.Result result = stock.issue(
                    document.getWarehouseId(), document.getLocationId(), item.getId(), lotId,
                    shortage, item.getCode());
            line.setUnitCost(result.avgCost());
            record(document, line, MovementType.COUNT_LOSS, item, lotId,
                    document.getWarehouseId(), document.getLocationId(),
                    shortage, result.avgCost(), result.movementAmount(),
                    result.qty(), result.totalAmount(), line.getRemark());
        }
    }

    private void record(StockDocument document,
                        DocumentLine line,
                        MovementType type,
                        Item item,
                        Long lotId,
                        Long warehouseId,
                        Long locationId,
                        BigDecimal qty,
                        BigDecimal unitCost,
                        BigDecimal amount,
                        BigDecimal balanceQty,
                        BigDecimal balanceAmount,
                        String remark) {
        Movement movement = new Movement();
        movement.setDocumentId(document.getId());
        movement.setDocumentLineId(line == null ? null : line.getId());
        movement.setMovementType(type);
        movement.setDirection(type.isInbound() ? "IN" : "OUT");
        movement.setItemId(item.getId());
        movement.setLotId(lotId);
        movement.setWarehouseId(warehouseId);
        movement.setLocationId(locationId);
        movement.setQty(qty);
        movement.setUnitCost(unitCost == null ? BigDecimal.ZERO : unitCost);
        movement.setAmount(amount == null ? BigDecimal.ZERO : amount);
        movement.setBalanceQtyAfter(balanceQty);
        movement.setBalanceAmountAfter(balanceAmount);
        movement.setRefType(document.getSourceType() == null ? "INVENTORY" : document.getSourceType());
        movement.setRefId(document.getSourceType() == null ? document.getDocNo() : document.getSourceId());
        movement.setOperator(CurrentUser.username().orElse(null));
        movement.setRemark(remark);
        movement.setMovedAt(Instant.now());
        movements.save(movement);
    }

    // ------------------------------------------------------------- internals

    private List<DocumentLine> buildLines(List<DocumentDtos.LineRequest> requests) {
        List<DocumentLine> lines = new ArrayList<>();
        int lineNo = 10;
        for (DocumentDtos.LineRequest request : requests) {
            if (request.qty() == null || request.qty().signum() < 0) {
                throw ApiException.badRequest("A document line needs a quantity of zero or more");
            }
            DocumentLine line = new DocumentLine(lineNo, request.itemId(), request.qty());
            line.setLotId(request.lotId());
            line.setUnitCost(request.unitCost() == null ? BigDecimal.ZERO : request.unitCost());
            line.setCountedQty(request.countedQty());
            line.setRemark(blankToNull(request.remark()));
            lines.add(line);
            lineNo += 10;
        }
        return lines;
    }

    /** The lot on a line, created on the spot when the item is tracked. */
    private Long resolveLot(Item item, DocumentLine line) {
        if (line.getLotId() != null) {
            Lot lot = lots.require(line.getLotId());
            if (!lot.getItemId().equals(item.getId())) {
                throw ApiException.badRequest("Lot " + lot.getLotNo() + " belongs to a different item");
            }
            return lot.getId();
        }
        return null;
    }

    private void validatePartner(DocumentDtos.DocumentRequest request) {
        if (request.partnerId() == null) {
            return;
        }
        Partner partner = partners.findById(request.partnerId())
                .orElseThrow(() -> ApiException.badRequest(
                        "Partner " + request.partnerId() + " does not exist"));
        boolean inbound = request.docType() == DocumentType.INBOUND;
        boolean allowed = inbound
                ? partner.getPartnerType().tradesAsSupplier()
                : partner.getPartnerType().tradesAsCustomer();
        if (!allowed) {
            throw ApiException.badRequest("Partner " + partner.getCode() + " is not a "
                    + (inbound ? "supplier" : "customer"));
        }
    }

    private Item requireItem(Long itemId) {
        return items.findById(itemId)
                .orElseThrow(() -> ApiException.badRequest("Item " + itemId + " does not exist"));
    }

    StockDocument require(Long id) {
        return documents.findById(id)
                .orElseThrow(() -> ApiException.notFound("Document " + id + " does not exist"));
    }

    private Specification<StockDocument> specification(String keyword,
                                                        DocumentType docType,
                                                        DocumentStatus status,
                                                        Long warehouseId) {
        Specification<StockDocument> spec = (root, query, cb) -> cb.conjunction();
        if (docType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("docType"), docType));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (warehouseId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.or(cb.equal(root.get("warehouseId"), warehouseId),
                            cb.equal(root.get("toWarehouseId"), warehouseId)));
        }
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.trim().toLowerCase(java.util.Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("docNo")), "%" + term + "%"));
        }
        return spec;
    }

    List<DocumentDtos.DocumentView> views(List<StockDocument> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        Map<Long, Item> itemById = items
                .findAllById(source.stream().flatMap(document -> document.getLines().stream())
                        .map(DocumentLine::getItemId).distinct().toList())
                .stream().collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<Long, Warehouse> warehouseById = warehouseRepository
                .findAllById(source.stream()
                        .flatMap(document -> java.util.stream.Stream.of(
                                document.getWarehouseId(), document.getToWarehouseId()))
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        Map<Long, Partner> partnerById = partners
                .findAllById(source.stream().map(StockDocument::getPartnerId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Partner::getId, Function.identity()));
        Map<Long, Lot> lotById = lotRepository
                .findAllById(source.stream().flatMap(document -> document.getLines().stream())
                        .map(DocumentLine::getLotId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Lot::getId, Function.identity()));
        Map<Long, Location> locationById = locationRepository
                .findAllById(source.stream()
                        .flatMap(document -> java.util.stream.Stream.of(
                                document.getLocationId(), document.getToLocationId()))
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Location::getId, Function.identity()));

        return source.stream().map(document -> {
            Warehouse warehouse = warehouseById.get(document.getWarehouseId());
            Warehouse destination = warehouseById.get(document.getToWarehouseId());
            Partner partner = partnerById.get(document.getPartnerId());
            List<DocumentDtos.LineView> lines = document.getLines().stream()
                    .map(line -> {
                        Item item = itemById.get(line.getItemId());
                        return new DocumentDtos.LineView(
                                line.getId(),
                                line.getLineNo(),
                                line.getItemId(),
                                item == null ? null : item.getCode(),
                                item == null ? null : item.getName(),
                                item == null ? null : item.getUomCode(),
                                line.getLotId(),
                                lotNoOf(line.getLotId(), lotById),
                                line.getQty(),
                                line.getUnitCost(),
                                line.getCountedQty(),
                                line.getQty().multiply(line.getUnitCost()),
                                line.getRemark());
                    })
                    .toList();
            BigDecimal totalQty = lines.stream().map(DocumentDtos.LineView::qty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalAmount = lines.stream().map(DocumentDtos.LineView::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            StockDocument reversed = document.getReversalOf() == null
                    ? null : documents.findById(document.getReversalOf()).orElse(null);
            return new DocumentDtos.DocumentView(
                    document.getId(),
                    document.getDocNo(),
                    document.getDocType(),
                    document.getStatus(),
                    document.getWarehouseId(),
                    warehouse == null ? null : warehouse.getCode(),
                    warehouse == null ? null : warehouse.getName(),
                    document.getLocationId(),
                    locationName(locationById.get(document.getLocationId())),
                    document.getToWarehouseId(),
                    destination == null ? null : destination.getName(),
                    document.getToLocationId(),
                    locationName(locationById.get(document.getToLocationId())),
                    document.getPartnerId(),
                    partner == null ? null : partner.getName(),
                    document.getBizDate(),
                    document.getRemark(),
                    document.getReversalOf(),
                    reversed == null ? null : reversed.getDocNo(),
                    document.getPostedAt(),
                    document.getCreatedBy(),
                    document.getRowVersion(),
                    totalQty,
                    totalAmount,
                    lines);
        }).toList();
    }

    /** One document rendered the same way a list row is rendered. */
    DocumentDtos.DocumentView view(StockDocument document) {
        return views(List.of(document)).get(0);
    }

    private String lotNoOf(Long lotId, Map<Long, Lot> lotById) {        if (lotId == null) {
            return null;
        }
        Lot lot = lotById.get(lotId);
        return lot == null ? null : lot.getLotNo();
    }

    private String locationName(Location location) {
        if (location == null) {
            return null;
        }
        return location.getCode() + " · " + location.getName();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 200);
    }
}
