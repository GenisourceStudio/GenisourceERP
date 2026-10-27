package com.genisource.erp.sales;

import com.genisource.erp.finance.ledger.ReceivableService;
import com.genisource.erp.inventory.document.DocumentDtos;
import com.genisource.erp.inventory.document.DocumentService;
import com.genisource.erp.inventory.document.DocumentType;
import com.genisource.erp.inventory.lot.Lot;
import com.genisource.erp.inventory.lot.LotRepository;
import com.genisource.erp.inventory.stock.StockDtos;
import com.genisource.erp.inventory.stock.StockService;
import com.genisource.erp.inventory.warehouse.Location;
import com.genisource.erp.inventory.warehouse.LocationRepository;
import com.genisource.erp.inventory.warehouse.Warehouse;
import com.genisource.erp.inventory.warehouse.WarehouseRepository;
import com.genisource.erp.masterdata.item.Item;
import com.genisource.erp.masterdata.item.ItemRepository;
import com.genisource.erp.masterdata.partner.Partner;
import com.genisource.erp.masterdata.partner.PartnerRepository;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.DocumentLifecycle;
import com.genisource.erp.platform.common.PageResponse;
import com.genisource.erp.platform.numbering.DocumentNoAllocator;
import com.genisource.erp.platform.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The sell side: a quotation becomes an order, an order promises stock, and a shipment
 * is what actually gives the stock away and raises the claim.
 *
 * <p>Confirming an order reserves stock. That is the whole point of the reservation
 * table: without it, two salesmen could each sell the same fifty units, and the first
 * to ship would silently make the second one a liar.
 *
 * <p>Posting a shipment does three things or none of them: the promise is given back,
 * the stock is issued, and the receivable appears. One transaction, so a shipment that
 * cannot move stock never raises a claim either.
 */
@Service
@Transactional(readOnly = true)
public class SalesService {

    /** What a stock document says about where it came from. */
    public static final String SOURCE_TYPE = "SAL_SHIPMENT";

    private final SalesRepository documents;
    private final ReservationRepository reservations;
    private final PartnerRepository partners;
    private final ItemRepository items;
    private final WarehouseRepository warehouses;
    private final LocationRepository locations;
    private final LotRepository lots;
    private final StockService stock;
    private final DocumentService stockDocuments;
    private final ReceivableService receivables;
    private final DocumentNoAllocator numbers;
    private final AuditService audit;

    private final BigDecimal approvalThreshold;
    private final BigDecimal defaultTaxRate;

    public SalesService(SalesRepository documents,
                        ReservationRepository reservations,
                        PartnerRepository partners,
                        ItemRepository items,
                        WarehouseRepository warehouses,
                        LocationRepository locations,
                        LotRepository lots,
                        StockService stock,
                        DocumentService stockDocuments,
                        ReceivableService receivables,
                        DocumentNoAllocator numbers,
                        AuditService audit,
                        @Value("${erp.sales.approval-threshold:5000}") BigDecimal approvalThreshold,
                        @Value("${erp.trade.default-tax-rate:0.13}") BigDecimal defaultTaxRate) {
        this.documents = documents;
        this.reservations = reservations;
        this.partners = partners;
        this.items = items;
        this.warehouses = warehouses;
        this.locations = locations;
        this.lots = lots;
        this.stock = stock;
        this.stockDocuments = stockDocuments;
        this.receivables = receivables;
        this.numbers = numbers;
        this.audit = audit;
        this.approvalThreshold = approvalThreshold;
        this.defaultTaxRate = defaultTaxRate;
    }

    // ------------------------------------------------------------------- read

    public PageResponse<SalesDtos.DocumentView> search(SalesDocType type,
                                                       String keyword,
                                                       Long partnerId,
                                                       DocumentLifecycle status,
                                                       LocalDate from,
                                                       LocalDate to,
                                                       int page,
                                                       int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by("id").descending());
        Page<SalesDocument> result = documents.findAll(
                specification(type, keyword, partnerId, status, from, to), pageable);
        return PageResponse.of(result, views(result.getContent()));
    }

    public SalesDtos.DocumentView get(Long id) {
        return views(List.of(require(id))).get(0);
    }

    public SalesDtos.DocumentView get(Long id, SalesDocType expected) {
        SalesDocument document = require(id);
        if (document.getDocType() != expected) {
            throw ApiException.badRequest("Document " + document.getDocNo() + " is a "
                    + document.getDocType().name().toLowerCase() + ", not a "
                    + expected.name().toLowerCase());
        }
        return views(List.of(document)).get(0);
    }

    // ------------------------------------------------------------------ write

    @Transactional
    public SalesDtos.DocumentView create(SalesDocType type, SalesDtos.DocumentRequest request) {
        Partner partner = resolvePartner(type, request.partnerId());
        Warehouse warehouse = resolveWarehouse(type, request.warehouseId());
        if (request.locationId() != null && warehouse != null) {
            requireLocation(request.locationId(), warehouse.getId());
        }
        SalesDocument document = new SalesDocument(
                numbers.next(type.prefix(), request.bizDate()),
                type,
                warehouse == null ? null : warehouse.getId(),
                request.bizDate());
        apply(document, request, partner);
        documents.save(document);

        audit.record("sales." + type.name().toLowerCase() + ".create",
                CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "sal_document", String.valueOf(document.getId()),
                Map.of("docNo", document.getDocNo()));
        return views(List.of(document)).get(0);
    }

    @Transactional
    public SalesDtos.DocumentView update(Long id, SalesDtos.DocumentRequest request) {
        SalesDocument document = require(id);
        if (!document.getStatus().isEditable()) {
            throw ApiException.invalidState("Document " + document.getDocNo()
                    + " is no longer a draft and cannot be edited");
        }
        Partner partner = resolvePartner(document.getDocType(), request.partnerId());
        Warehouse warehouse = resolveWarehouse(document.getDocType(), request.warehouseId());
        if (request.locationId() != null && warehouse != null) {
            requireLocation(request.locationId(), warehouse.getId());
        }
        apply(document, request, partner);
        documents.save(document);

        audit.record("sales." + document.getDocType().name().toLowerCase() + ".update",
                CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "sal_document", String.valueOf(id), Map.of("docNo", document.getDocNo()));
        return views(List.of(document)).get(0);
    }

    @Transactional
    public void delete(Long id) {
        SalesDocument document = require(id);
        if (!document.getStatus().isEditable()) {
            throw ApiException.invalidState("Document " + document.getDocNo()
                    + " is no longer a draft and cannot be deleted");
        }
        if (document.getDocType() == SalesDocType.SHIPMENT) {
            throw ApiException.invalidState("A shipment cannot be deleted; reverse it instead");
        }
        documents.delete(document);
        audit.record("sales." + document.getDocType().name().toLowerCase() + ".delete",
                CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "sal_document", String.valueOf(id), Map.of("docNo", document.getDocNo()));
    }

    // ------------------------------------------------------------- life cycle

    @Transactional
    public SalesDtos.DocumentView submit(Long id) {
        SalesDocument document = require(id);
        if (document.getStatus() != DocumentLifecycle.DRAFT) {
            throw ApiException.invalidState("Only a draft can be submitted");
        }
        String operator = CurrentUser.username().orElse("system");
        document.submit(operator);
        if (document.getTotalAmount().compareTo(approvalThreshold) < 0) {
            document.approve(operator, "Auto-approved: below the approval threshold of "
                    + approvalThreshold.stripTrailingZeros().toPlainString());
            if (document.getDocType().reservesStock()) {
                reserve(document);
            }
        }
        documents.save(document);
        audit.record("sales.submit", CurrentUser.id().orElse(null), operator,
                "sal_document", String.valueOf(id),
                Map.of("docNo", document.getDocNo(), "status", document.getStatus().name()));
        return views(List.of(document)).get(0);
    }

    /**
     * Approves, and for an order that means promising the stock.
     *
     * <p>Confirmation and reservation are one act on purpose. An approved order that
     * had not reserved anything would let the same units be sold again, which is the
     * failure a customer discovers on the day the goods do not turn up.
     */
    @Transactional
    public SalesDtos.DocumentView approve(Long id, String remark) {
        SalesDocument document = require(id);
        if (document.getStatus() != DocumentLifecycle.SUBMITTED) {
            throw ApiException.invalidState("Only a submitted document can be approved; "
                    + document.getDocNo() + " is " + document.getStatus().name());
        }
        document.approve(CurrentUser.username().orElse("system"), blankToNull(remark));
        if (document.getDocType().reservesStock()) {
            reserve(document);
        }
        documents.save(document);
        audit.record("sales.approve", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "sal_document", String.valueOf(id),
                Map.of("docNo", document.getDocNo()));
        return views(List.of(document)).get(0);
    }

    @Transactional
    public SalesDtos.DocumentView reject(Long id, String remark) {
        SalesDocument document = require(id);
        if (document.getStatus() != DocumentLifecycle.SUBMITTED) {
            throw ApiException.invalidState("Only a submitted document can be rejected; "
                    + document.getDocNo() + " is " + document.getStatus().name());
        }
        document.reject(CurrentUser.username().orElse("system"), blankToNull(remark));
        documents.save(document);
        audit.record("sales.reject", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "sal_document", String.valueOf(id),
                Map.of("docNo", document.getDocNo()));
        return views(List.of(document)).get(0);
    }

    @Transactional
    public SalesDtos.DocumentView cancel(Long id) {
        SalesDocument document = require(id);
        if (!document.getStatus().isOpen()) {
            throw ApiException.invalidState("Document " + document.getDocNo()
                    + " is " + document.getStatus().name() + " and cannot be cancelled");
        }
        if (document.getDocType() == SalesDocType.SHIPMENT) {
            throw ApiException.invalidState("A shipment cannot be cancelled; reverse it instead");
        }
        if (document.getDocType().reservesStock()) {
            releaseAll(document);
        }
        document.setStatus(DocumentLifecycle.CANCELLED);
        documents.save(document);
        audit.record("sales.cancel", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "sal_document", String.valueOf(id),
                Map.of("docNo", document.getDocNo()));
        return views(List.of(document)).get(0);
    }

    @Transactional
    public SalesDtos.DocumentView close(Long id) {
        SalesDocument document = require(id);
        if (document.getStatus().isTerminal()) {
            throw ApiException.invalidState("Document " + document.getDocNo()
                    + " is already " + document.getStatus().name());
        }
        if (document.getDocType().reservesStock()) {
            releaseAll(document);
        }
        document.setStatus(DocumentLifecycle.CLOSED);
        documents.save(document);
        audit.record("sales.close", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "sal_document", String.valueOf(id),
                Map.of("docNo", document.getDocNo()));
        return views(List.of(document)).get(0);
    }

    // ------------------------------------------------------------------- flow

    /** Turns an approved quotation into a sales order. */
    @Transactional
    public SalesDtos.DocumentView convertToOrder(Long quotationId, SalesDtos.ConvertRequest request) {
        SalesDocument quotation = require(quotationId);
        if (quotation.getDocType() != SalesDocType.QUOTATION) {
            throw ApiException.badRequest("Only a quotation can be converted to an order");
        }
        if (quotation.getStatus() != DocumentLifecycle.APPROVED) {
            throw ApiException.invalidState("Quotation " + quotation.getDocNo() + " is "
                    + quotation.getStatus().name() + "; approve it before converting");
        }
        Partner partner = request.partnerId() == null
                ? (quotation.getPartnerId() == null ? null : requirePartner(quotation.getPartnerId()))
                : requirePartner(request.partnerId());
        if (partner == null) {
            throw ApiException.badRequest("An order needs a customer: name one on the request, "
                    + "or on the quotation before converting it");
        }
        if (!partner.getPartnerType().tradesAsCustomer()) {
            throw ApiException.badRequest("Partner " + partner.getCode() + " is not a customer");
        }
        LocalDate bizDate = request.bizDate() == null ? LocalDate.now() : request.bizDate();

        List<SalesDocumentLine> picks = new ArrayList<>();
        for (SalesDocumentLine line : quotation.getLines()) {
            BigDecimal open = line.openQty();
            if (open.signum() <= 0) {
                continue;
            }
            BigDecimal take = quantityFor(request.lines(), line.getId(), open);
            if (take.signum() <= 0) {
                continue;
            }
            if (take.compareTo(open) > 0) {
                throw ApiException.badRequest("Only " + open.stripTrailingZeros().toPlainString()
                        + " of " + itemCode(line.getItemId()) + " is still open on "
                        + quotation.getDocNo());
            }
            SalesDocumentLine copy = new SalesDocumentLine(
                    (picks.size() + 1) * 10, line.getItemId(), take, line.getUnitPrice(),
                    line.getTaxRate(), line.getRemark());
            copy.setSourceLineId(line.getId());
            copy.setWarehouseId(line.getWarehouseId());
            picks.add(copy);
        }
        if (picks.isEmpty()) {
            throw ApiException.badRequest("Quotation " + quotation.getDocNo()
                    + " has nothing left to order");
        }

        SalesDocument order = new SalesDocument(
                numbers.next(SalesDocType.ORDER.prefix(), bizDate),
                SalesDocType.ORDER, quotation.getWarehouseId(), bizDate);
        order.setLocationId(quotation.getLocationId());
        order.setPartnerId(partner.getId());
        order.setPaymentTermDays(partner.getPaymentTermsDays());
        order.setSourceType("SAL_QUOTATION");
        order.setSourceId(quotation.getDocNo());
        order.setRemark(blankToNull(request.remark()));
        order.replaceLines(picks);
        order.recalculate();
        documents.save(order);

        for (SalesDocumentLine pick : picks) {
            SalesDocumentLine source = quotation.getLines().stream()
                    .filter(line -> line.getId().equals(pick.getSourceLineId()))
                    .findFirst().orElseThrow();
            source.fulfil(pick.getQty());
        }
        quotation.setStatus(quotation.isFulfilled()
                ? DocumentLifecycle.COMPLETED : DocumentLifecycle.PARTIAL);
        documents.save(quotation);

        audit.record("sales.convert", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "sal_document", String.valueOf(order.getId()),
                Map.of("order", order.getDocNo(), "from", quotation.getDocNo()));
        return views(List.of(order)).get(0);
    }

    /** Records goods leaving against an order, as a draft shipment. */
    @Transactional
    public SalesDtos.DocumentView ship(Long orderId, SalesDtos.ShipRequest request) {
        SalesDocument order = require(orderId);
        if (order.getDocType() != SalesDocType.ORDER) {
            throw ApiException.badRequest("Goods can only be shipped against a sales order");
        }
        if (order.getStatus() != DocumentLifecycle.APPROVED
                && order.getStatus() != DocumentLifecycle.PARTIAL) {
            throw ApiException.invalidState("Order " + order.getDocNo() + " is "
                    + order.getStatus().name() + "; confirm it before shipping against it");
        }
        Warehouse warehouse = requireUsableWarehouse(request.warehouseId());
        if (request.locationId() != null) {
            requireLocation(request.locationId(), warehouse.getId());
        }

        List<SalesDocumentLine> lines = new ArrayList<>();
        for (SalesDtos.ShipLine pick : request.lines()) {
            SalesDocumentLine source = order.getLines().stream()
                    .filter(line -> line.getId().equals(pick.orderLineId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest(
                            "Line " + pick.orderLineId() + " is not part of " + order.getDocNo()));
            BigDecimal open = source.openQty();
            if (pick.qty().compareTo(open) > 0) {
                throw ApiException.badRequest("Only " + open.stripTrailingZeros().toPlainString()
                        + " of " + itemCode(source.getItemId()) + " is still open on "
                        + order.getDocNo());
            }
            SalesDocumentLine line = new SalesDocumentLine(
                    (lines.size() + 1) * 10, source.getItemId(), pick.qty(),
                    source.getUnitPrice(), source.getTaxRate(), source.getRemark());
            line.setSourceLineId(source.getId());
            line.setWarehouseId(warehouse.getId());
            line.setLocationId(request.locationId());
            line.setLotId(pick.lotId());
            lines.add(line);
        }

        SalesDocument shipment = new SalesDocument(
                numbers.next(SalesDocType.SHIPMENT.prefix(), request.bizDate()),
                SalesDocType.SHIPMENT, warehouse.getId(), request.bizDate());
        shipment.setLocationId(request.locationId());
        shipment.setPartnerId(order.getPartnerId());
        shipment.setPaymentTermDays(order.getPaymentTermDays());
        shipment.setSourceType("SAL_ORDER");
        shipment.setSourceId(order.getDocNo());
        shipment.setRemark(blankToNull(request.remark()));
        shipment.replaceLines(lines);
        shipment.recalculate();
        documents.save(shipment);

        audit.record("sales.ship", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "sal_document", String.valueOf(shipment.getId()),
                Map.of("shipment", shipment.getDocNo(), "order", order.getDocNo()));
        return views(List.of(shipment)).get(0);
    }

    /**
     * Posts a shipment: the promise is given back, the stock leaves, the claim appears.
     *
     * <p>The reservation has to be released before the stock is issued. Issue valuations
     * are checked against what is free, and stock that is still promised to this very
     * order is not free — so releasing first is not bookkeeping pedantry, it is what
     * lets the issue pass.
     */
    @Transactional
    public SalesDtos.DocumentView post(Long shipmentId) {
        SalesDocument shipment = require(shipmentId);
        if (shipment.getDocType() != SalesDocType.SHIPMENT) {
            throw ApiException.badRequest("Only a shipment can be posted");
        }
        if (shipment.getStatus() != DocumentLifecycle.DRAFT) {
            throw ApiException.invalidState("Shipment " + shipment.getDocNo() + " is "
                    + shipment.getStatus().name() + " and cannot be posted");
        }
        if (shipment.getPartnerId() == null) {
            throw ApiException.badRequest("Shipment " + shipment.getDocNo()
                    + " has no customer, so no receivable can be raised");
        }
        requireUsableWarehouse(shipment.getWarehouseId());

        // Promised stock is consumed oldest promise first, and grouped by the balance
        // line it sat on: a shipment that draws from two bins writes two stock documents
        // rather than pretending one bin supplied everything.
        Map<StockKey, List<DocumentDtos.LineRequest>> byKey = new LinkedHashMap<>();
        for (SalesDocumentLine line : shipment.getLines()) {
            BigDecimal remaining = line.getQty();
            List<Reservation> held = line.getSourceLineId() == null
                    ? List.of()
                    : reservations.findByOrderLineIdAndStatusOrderByIdAsc(
                            line.getSourceLineId(), ReservationStatus.OPEN);
            for (Reservation reservation : held) {
                if (remaining.signum() <= 0) {
                    break;
                }
                BigDecimal take = reservation.openQty().min(remaining);
                if (take.signum() <= 0) {
                    continue;
                }
                stock.releaseReservation(reservation.getWarehouseId(), reservation.getLocationId(),
                        reservation.getItemId(), reservation.getLotId(), take,
                        itemCode(reservation.getItemId()));
                reservation.issue(take);
                reservations.save(reservation);
                byKey.computeIfAbsent(
                                new StockKey(reservation.getWarehouseId(), reservation.getLocationId()),
                                key -> new ArrayList<>())
                        .add(new DocumentDtos.LineRequest(reservation.getItemId(),
                                reservation.getLotId(), take, null, null, line.getRemark()));
                remaining = remaining.subtract(take);
            }
            if (remaining.signum() > 0) {
                throw ApiException.insufficientStock(
                        "Only " + line.getQty().subtract(remaining).stripTrailingZeros().toPlainString()
                                + " of " + itemCode(line.getItemId())
                                + " is promised to this order, so "
                                + line.getQty().stripTrailingZeros().toPlainString()
                                + " cannot be shipped");
            }
        }

        for (Map.Entry<StockKey, List<DocumentDtos.LineRequest>> entry : byKey.entrySet()) {
            DocumentDtos.DocumentRequest stockRequest = new DocumentDtos.DocumentRequest(
                    DocumentType.OUTBOUND, entry.getKey().warehouseId(), entry.getKey().locationId(),
                    null, null, shipment.getPartnerId(), shipment.getBizDate(),
                    "From sales shipment " + shipment.getDocNo(), entry.getValue());
            stockDocuments.postSourced(stockRequest, SOURCE_TYPE, shipment.getDocNo());
        }

        LocalDate dueDate = shipment.getBizDate()
                .plusDays(Math.max(shipment.getPaymentTermDays(), 0));
        receivables.createFrom(SOURCE_TYPE, shipment.getId(), shipment.getDocNo(),
                shipment.getPartnerId(), shipment.getBizDate(), dueDate,
                shipment.getNetAmount(), shipment.getTaxAmount(), shipment.getTotalAmount(),
                "Sales shipment " + shipment.getDocNo());

        applyFulfilment(shipment, true);
        shipment.post(CurrentUser.username().orElse("system"));
        documents.save(shipment);

        audit.record("sales.shipment.post", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "sal_document", String.valueOf(shipment.getId()),
                Map.of("docNo", shipment.getDocNo(),
                        "amount", shipment.getTotalAmount().toPlainString()));
        return views(List.of(shipment)).get(0);
    }

    /**
     * Undoes a posted shipment: the goods come back, the claim is voided, and the order
     * is promised the stock again so it can be shipped a second time.
     */
    @Transactional
    public SalesDtos.DocumentView reverse(Long shipmentId) {
        SalesDocument shipment = require(shipmentId);
        if (shipment.getDocType() != SalesDocType.SHIPMENT) {
            throw ApiException.badRequest("Only a shipment can be reversed");
        }
        if (shipment.getStatus() != DocumentLifecycle.POSTED) {
            throw ApiException.invalidState("Only a posted shipment can be reversed; "
                    + shipment.getDocNo() + " is " + shipment.getStatus().name());
        }
        stockDocuments.reverseSourced(SOURCE_TYPE, shipment.getDocNo());
        receivables.voidSource(SOURCE_TYPE, shipmentId);
        applyFulfilment(shipment, false);
        shipment.markReversed();
        documents.save(shipment);

        SalesDocument order = shipment.getSourceId() == null
                ? null : documents.findByDocNo(shipment.getSourceId()).orElse(null);
        if (order != null && order.getStatus().isOpen()) {
            reserve(order);
            documents.save(order);
        }

        audit.record("sales.shipment.reverse", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "sal_document", String.valueOf(shipment.getId()),
                Map.of("docNo", shipment.getDocNo()));
        return views(List.of(shipment)).get(0);
    }

    // ---------------------------------------------------------- reservations

    /**
     * Promises stock to an order, line by line, fullest balance line first.
     *
     * <p>Draining the fullest line is what a picker does anyway; taking a little from
     * each is how a warehouse ends up with dozens of near-empty bins. Where one line
     * cannot cover the quantity, the rest comes from the next, which is why one order
     * line can hold several reservations.
     */
    private void reserve(SalesDocument order) {
        if (order.getWarehouseId() == null) {
            throw ApiException.badRequest("Order " + order.getDocNo()
                    + " needs the warehouse it will ship from before stock can be promised");
        }
        requireUsableWarehouse(order.getWarehouseId());
        for (SalesDocumentLine line : order.getLines()) {
            BigDecimal wanted = line.getQty().subtract(reservedQtyOf(line.getId()));
            if (wanted.signum() <= 0) {
                continue;
            }
            BigDecimal remaining = wanted;
            for (StockDtos.ReservableLine candidate
                    : stock.reservable(order.getWarehouseId(), line.getItemId())) {
                if (remaining.signum() <= 0) {
                    break;
                }
                BigDecimal take = candidate.available().min(remaining);
                if (take.signum() <= 0) {
                    continue;
                }
                stock.reserve(candidate.warehouseId(), candidate.locationId(), candidate.itemId(),
                        candidate.lotId(), take, itemCode(line.getItemId()));
                reservations.save(new Reservation(order.getId(), line.getId(),
                        candidate.warehouseId(), candidate.locationId(), candidate.itemId(),
                        candidate.lotId(), take));
                remaining = remaining.subtract(take);
            }
            if (remaining.signum() > 0) {
                throw ApiException.insufficientStock(
                        "Not enough free stock of " + itemCode(line.getItemId()) + " to promise "
                                + wanted.stripTrailingZeros().toPlainString()
                                + " to " + order.getDocNo() + "; "
                                + wanted.subtract(remaining).stripTrailingZeros().toPlainString()
                                + " could be promised");
            }
        }
    }

    /** Gives back everything an order is still holding. */
    private void releaseAll(SalesDocument order) {
        for (Reservation reservation
                : reservations.findByOrderIdAndStatusOrderByIdAsc(order.getId(),
                        ReservationStatus.OPEN)) {
            BigDecimal held = reservation.openQty();
            if (held.signum() <= 0) {
                continue;
            }
            stock.releaseReservation(reservation.getWarehouseId(), reservation.getLocationId(),
                    reservation.getItemId(), reservation.getLotId(), held,
                    itemCode(reservation.getItemId()));
            reservation.release();
            reservations.save(reservation);
        }
    }

    /**
     * How much of one order line is still promised.
     *
     * <p>Read from the reservations rather than stored on the line: a counter kept next
     * to the thing it counts is a counter waiting to drift.
     */
    private BigDecimal reservedQtyOf(Long orderLineId) {
        return reservations.findByOrderLineIdAndStatusOrderByIdAsc(orderLineId,
                        ReservationStatus.OPEN)
                .stream()
                .map(Reservation::openQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Which balance line a group of shipment lines draws from. */
    private record StockKey(Long warehouseId, Long locationId) {
    }

    // --------------------------------------------------------------- helpers

    private void applyFulfilment(SalesDocument shipment, boolean forward) {
        String orderNo = shipment.getSourceId();
        if (orderNo == null) {
            return;
        }
        SalesDocument order = documents.findByDocNo(orderNo).orElse(null);
        if (order == null) {
            return;
        }
        for (SalesDocumentLine line : shipment.getLines()) {
            SalesDocumentLine source = order.getLines().stream()
                    .filter(candidate -> candidate.getId().equals(line.getSourceLineId()))
                    .findFirst().orElse(null);
            if (source == null) {
                continue;
            }
            if (forward) {
                source.fulfil(line.getQty());
            } else {
                source.unfulfil(line.getQty());
            }
        }
        if (order.isFulfilled()) {
            order.setStatus(DocumentLifecycle.COMPLETED);
        } else if (order.isPartlyFulfilled()) {
            order.setStatus(DocumentLifecycle.PARTIAL);
        } else {
            order.setStatus(DocumentLifecycle.APPROVED);
        }
        documents.save(order);
    }

    private void apply(SalesDocument document, SalesDtos.DocumentRequest request, Partner partner) {
        document.setPartnerId(partner == null ? null : partner.getId());
        document.setWarehouseId(request.warehouseId());
        document.setLocationId(request.locationId());
        document.setBizDate(request.bizDate());
        document.setRequiredDate(request.requiredDate());
        document.setPaymentTermDays(resolveTermDays(partner, request.paymentTermDays()));
        document.setRemark(blankToNull(request.remark()));
        document.replaceLines(buildLines(request.lines()));
        document.recalculate();
    }

    private List<SalesDocumentLine> buildLines(List<SalesDtos.LineRequest> requests) {
        List<SalesDocumentLine> lines = new ArrayList<>();
        int index = 0;
        for (SalesDtos.LineRequest line : requests) {
            if (!items.existsById(line.itemId())) {
                throw ApiException.badRequest("Item " + line.itemId() + " does not exist");
            }
            BigDecimal taxRate = line.taxRate() == null ? defaultTaxRate : line.taxRate();
            SalesDocumentLine built = new SalesDocumentLine(
                    ++index * 10, line.itemId(), line.qty(),
                    line.unitPrice() == null ? BigDecimal.ZERO : line.unitPrice(),
                    taxRate, blankToNull(line.remark()));
            built.setLotId(line.lotId());
            lines.add(built);
        }
        return lines;
    }

    private int resolveTermDays(Partner partner, Integer requested) {
        if (requested != null) {
            return Math.max(requested, 0);
        }
        return partner == null ? 0 : Math.max(partner.getPaymentTermsDays(), 0);
    }

    private Partner resolvePartner(SalesDocType type, Long partnerId) {
        if (partnerId == null) {
            if (type.needsPartner()) {
                throw ApiException.badRequest("A " + type.name().toLowerCase() + " needs a customer");
            }
            return null;
        }
        Partner partner = requirePartner(partnerId);
        if (type.needsPartner() && !partner.getPartnerType().tradesAsCustomer()) {
            throw ApiException.badRequest("Partner " + partner.getCode() + " is not a customer");
        }
        return partner;
    }

    private Warehouse resolveWarehouse(SalesDocType type, Long warehouseId) {
        if (warehouseId == null) {
            if (type.needsWarehouse()) {
                throw ApiException.badRequest("A shipment needs the warehouse the goods leave from");
            }
            return null;
        }
        return requireUsableWarehouse(warehouseId);
    }

    private Partner requirePartner(Long id) {
        Partner partner = partners.findById(id)
                .orElseThrow(() -> ApiException.notFound("Partner " + id + " does not exist"));
        if (!partner.isActive()) {
            throw ApiException.invalidState("Partner " + partner.getCode() + " is inactive");
        }
        return partner;
    }

    private Warehouse requireUsableWarehouse(Long id) {
        if (id == null) {
            throw ApiException.badRequest("A warehouse is required");
        }
        Warehouse warehouse = warehouses.findById(id)
                .orElseThrow(() -> ApiException.notFound("Warehouse " + id + " does not exist"));
        if (warehouse.getStatus() != com.genisource.erp.platform.common.RecordStatus.ACTIVE) {
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

    private static BigDecimal quantityFor(List<SalesDtos.PickLine> picks,
                                          Long lineId,
                                          BigDecimal open) {
        if (picks == null || picks.isEmpty()) {
            return open;
        }
        return picks.stream()
                .filter(pick -> pick.lineId().equals(lineId))
                .findFirst()
                .map(SalesDtos.PickLine::qty)
                .orElse(open);
    }

    private String itemCode(Long itemId) {
        return items.findById(itemId).map(Item::getCode).orElse("item " + itemId);
    }

    private SalesDocument require(Long id) {
        return documents.findWithLinesById(id)
                .orElseThrow(() -> ApiException.notFound("Sales document " + id + " does not exist"));
    }

    private Specification<SalesDocument> specification(SalesDocType type,
                                                       String keyword,
                                                       Long partnerId,
                                                       DocumentLifecycle status,
                                                       LocalDate from,
                                                       LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(cb.equal(root.get("docType"), type));
            }
            if (partnerId != null) {
                predicates.add(cb.equal(root.get("partnerId"), partnerId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("bizDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("bizDate"), to));
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

    List<SalesDtos.DocumentView> views(List<SalesDocument> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        Map<Long, Partner> partnerById = partners
                .findAllById(source.stream().map(SalesDocument::getPartnerId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Partner::getId, Function.identity()));
        Map<Long, Warehouse> warehouseById = warehouses
                .findAllById(source.stream().map(SalesDocument::getWarehouseId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        Map<Long, Location> locationById = locations
                .findAllById(source.stream().map(SalesDocument::getLocationId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Location::getId, Function.identity()));
        Map<Long, Item> itemById = items
                .findAllById(source.stream().flatMap(document -> document.getLines().stream())
                        .map(SalesDocumentLine::getItemId).distinct().toList())
                .stream().collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<Long, Lot> lotById = lots
                .findAllById(source.stream().flatMap(document -> document.getLines().stream())
                        .map(SalesDocumentLine::getLotId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Lot::getId, Function.identity()));
        Map<Long, String> docNoById = documents
                .findAllById(source.stream().map(SalesDocument::getReversalOf)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(SalesDocument::getId, SalesDocument::getDocNo));

        // How much each order line is still promised, read from the reservations.
        Map<Long, BigDecimal> reservedByLine = new LinkedHashMap<>();
        for (SalesDocument document : source) {
            if (!document.getDocType().reservesStock()) {
                continue;
            }
            for (SalesDocumentLine line : document.getLines()) {
                reservedByLine.put(line.getId(), reservedQtyOf(line.getId()));
            }
        }

        return source.stream().map(document -> {
            Partner partner = partnerById.get(document.getPartnerId());
            Warehouse warehouse = warehouseById.get(document.getWarehouseId());
            Location location = locationById.get(document.getLocationId());
            BigDecimal totalQty = document.getLines().stream()
                    .map(SalesDocumentLine::getQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean stockReserved = document.getLines().stream()
                    .anyMatch(line -> reservedByLine.getOrDefault(line.getId(), BigDecimal.ZERO)
                            .signum() > 0);
            return new SalesDtos.DocumentView(
                    document.getId(),
                    document.getDocNo(),
                    document.getDocType(),
                    document.getStatus(),
                    document.getPartnerId(),
                    partner == null ? null : partner.getCode(),
                    partner == null ? null : partner.getName(),
                    document.getWarehouseId(),
                    warehouse == null ? null : warehouse.getCode(),
                    warehouse == null ? null : warehouse.getName(),
                    document.getLocationId(),
                    location == null ? null : location.getName(),
                    document.getBizDate(),
                    document.getRequiredDate(),
                    document.getPaymentTermDays(),
                    document.getNetAmount(),
                    document.getTaxAmount(),
                    document.getTotalAmount(),
                    totalQty,
                    stockReserved,
                    document.getSourceType(),
                    document.getSourceId(),
                    document.getSubmittedBy(),
                    document.getApprovedBy(),
                    document.getApprovedAt(),
                    document.getApprovalRemark(),
                    document.getPostedAt(),
                    document.getReversalOf(),
                    docNoById.get(document.getReversalOf()),
                    document.getRemark(),
                    document.getRowVersion(),
                    document.getLines().stream().map(line -> {
                        Item item = itemById.get(line.getItemId());
                        Lot lot = lotById.get(line.getLotId());
                        return new SalesDtos.LineView(
                                line.getId(), line.getLineNo(), line.getItemId(),
                                item == null ? null : item.getCode(),
                                item == null ? null : item.getName(),
                                item == null ? null : item.getUomCode(),
                                line.getQty(), line.getUnitPrice(), line.getTaxRate(),
                                line.getNetAmount(), line.getTaxAmount(), line.getTotalAmount(),
                                line.getQtyFulfilled(),
                                reservedByLine.getOrDefault(line.getId(), BigDecimal.ZERO),
                                line.openQty(),
                                line.getLotId(), lot == null ? null : lot.getLotNo(),
                                line.getSourceLineId(), line.getRemark());
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
