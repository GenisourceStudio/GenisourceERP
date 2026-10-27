package com.genisource.erp.purchase;

import com.genisource.erp.finance.ledger.PayableService;
import com.genisource.erp.inventory.document.DocumentDtos;
import com.genisource.erp.inventory.document.DocumentService;
import com.genisource.erp.inventory.document.DocumentType;
import com.genisource.erp.inventory.lot.Lot;
import com.genisource.erp.inventory.lot.LotRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The buy side: a requisition becomes an order, an order is received against, and the
 * receipt is what actually moves stock and creates the debt.
 *
 * <p>Posting a receipt is the one moment where three things have to happen or none of
 * them do: stock moves, the payable appears, and the order records what arrived. They
 * run in one transaction, so a receipt that cannot move stock never creates a debt
 * either.
 *
 * <p>Approval is deliberately small. A document above the configured threshold waits
 * for somebody to approve it; one below is approved as it is submitted, because asking
 * a manager to click through a two-hundred-yuan order is how approval becomes a rubber
 * stamp and stops being looked at.
 */
@Service
@Transactional(readOnly = true)
public class PurchaseService {

    /** What a stock document says about where it came from. */
    public static final String SOURCE_TYPE = "PUR_RECEIPT";

    /** What an order raised from a material plan records as its origin. */
    public static final String SOURCE_TYPE_MRP_PLAN = "MRP_PLAN";

    private final PurchaseRepository documents;
    private final PurchaseLineRepository documentLines;
    private final PartnerRepository partners;
    private final ItemRepository items;
    private final WarehouseRepository warehouses;
    private final LocationRepository locations;
    private final LotRepository lots;
    private final DocumentService stockDocuments;
    private final PayableService payables;
    private final DocumentNoAllocator numbers;
    private final AuditService audit;

    private final BigDecimal approvalThreshold;
    private final BigDecimal defaultTaxRate;

    public PurchaseService(PurchaseRepository documents,
                           PurchaseLineRepository documentLines,
                           PartnerRepository partners,
                           ItemRepository items,
                           WarehouseRepository warehouses,
                           LocationRepository locations,
                           LotRepository lots,
                           DocumentService stockDocuments,
                           PayableService payables,
                           DocumentNoAllocator numbers,
                           AuditService audit,
                           @Value("${erp.purchase.approval-threshold:5000}") BigDecimal approvalThreshold,
                           @Value("${erp.trade.default-tax-rate:0.13}") BigDecimal defaultTaxRate) {
        this.documents = documents;
        this.documentLines = documentLines;
        this.partners = partners;
        this.items = items;
        this.warehouses = warehouses;
        this.locations = locations;
        this.lots = lots;
        this.stockDocuments = stockDocuments;
        this.payables = payables;
        this.numbers = numbers;
        this.audit = audit;
        this.approvalThreshold = approvalThreshold;
        this.defaultTaxRate = defaultTaxRate;
    }

    // ------------------------------------------------------------------- read

    public PageResponse<PurchaseDtos.DocumentView> search(PurchaseDocType type,
                                                          String keyword,
                                                          Long partnerId,
                                                          DocumentLifecycle status,
                                                          LocalDate from,
                                                          LocalDate to,
                                                          int page,
                                                          int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by("id").descending());
        Page<PurchaseDocument> result = documents.findAll(
                specification(type, keyword, partnerId, status, from, to), pageable);
        return PageResponse.of(result, views(result.getContent()));
    }

    public PurchaseDtos.DocumentView get(Long id) {
        return views(List.of(require(id))).get(0);
    }

    /** Loads a document and refuses it unless it is the type the caller asked for. */
    public PurchaseDtos.DocumentView get(Long id, PurchaseDocType expected) {
        PurchaseDocument document = require(id);
        if (document.getDocType() != expected) {
            throw ApiException.badRequest("Document " + document.getDocNo() + " is a "
                    + document.getDocType().name().toLowerCase() + ", not a "
                    + expected.name().toLowerCase());
        }
        return views(List.of(document)).get(0);
    }

    // ------------------------------------------------------------------ write

    /**
     * Material already on order, one row per open order line.
     *
     * <p>What MRP nets demand against besides stock. Only approved and partially received
     * orders appear: a draft is a suggestion, and letting a suggestion reduce a shortage
     * would hide a gap that nobody has actually committed to closing.
     */
    public List<OpenSupplyLine> openSupply() {
        List<PurchaseDocument> orders = documents.findOpenWithLines(PurchaseDocType.ORDER,
                List.of(DocumentLifecycle.APPROVED, DocumentLifecycle.PARTIAL));
        List<OpenSupplyLine> supply = new ArrayList<>();
        for (PurchaseDocument order : orders) {
            LocalDate due = order.getRequiredDate() == null
                    ? order.getBizDate() : order.getRequiredDate();
            for (PurchaseDocumentLine line : order.getLines()) {
                BigDecimal open = line.openQty();
                if (open.signum() <= 0) {
                    continue;
                }
                supply.add(new OpenSupplyLine(line.getItemId(), open, due, order.getDocNo()));
            }
        }
        return supply;
    }

    /**
     * Raises an order from a material plan row.
     *
     * <p>Delegates to {@link #create} rather than assembling a document here: supplier and
     * warehouse validation, tax defaults and the audit entry must behave identically
     * whether a buyer typed the order or a planner generated it. The origin is stamped
     * afterwards, on the same transaction, so the order and its provenance are written
     * together or not at all.
     */
    @Transactional
    public PurchaseDtos.DocumentView createPlannedOrder(PlannedPurchase plan) {
        PurchaseDtos.DocumentRequest request = new PurchaseDtos.DocumentRequest(
                plan.supplierId(), null, null, plan.bizDate(), plan.requiredDate(), null,
                plan.remark(),
                List.of(new PurchaseDtos.LineRequest(plan.itemId(), plan.qty(),
                        plan.unitPrice(), plan.taxRate(), null, null)));
        PurchaseDtos.DocumentView created = create(PurchaseDocType.ORDER, request);

        PurchaseDocument order = require(created.id());
        order.setSourceType(SOURCE_TYPE_MRP_PLAN);
        order.setSourceId(plan.sourceId());
        documents.save(order);

        audit.record("purchase.order.from-plan", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "pur_document", String.valueOf(order.getId()),
                Map.of("docNo", order.getDocNo(), "plan", String.valueOf(plan.sourceId())));
        return views(List.of(order)).get(0);
    }

    @Transactional
    public PurchaseDtos.DocumentView create(PurchaseDocType type,
                                            PurchaseDtos.DocumentRequest request) {
        Partner partner = resolvePartner(type, request.partnerId());
        Warehouse warehouse = resolveWarehouse(type, request.warehouseId());
        if (request.locationId() != null && warehouse != null) {
            requireLocation(request.locationId(), warehouse.getId());
        }
        PurchaseDocument document = new PurchaseDocument(
                numbers.next(type.prefix(), request.bizDate()),
                type,
                warehouse == null ? null : warehouse.getId(),
                request.bizDate());
        apply(document, request, partner);
        documents.save(document);

        audit.record("purchase." + type.name().toLowerCase() + ".create",
                CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "pur_document", String.valueOf(document.getId()),
                Map.of("docNo", document.getDocNo()));
        return views(List.of(document)).get(0);
    }

    @Transactional
    public PurchaseDtos.DocumentView update(Long id, PurchaseDtos.DocumentRequest request) {
        PurchaseDocument document = require(id);
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

        audit.record("purchase." + document.getDocType().name().toLowerCase() + ".update",
                CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "pur_document", String.valueOf(id), Map.of("docNo", document.getDocNo()));
        return views(List.of(document)).get(0);
    }

    @Transactional
    public void delete(Long id) {
        PurchaseDocument document = require(id);
        if (!document.getStatus().isEditable()) {
            throw ApiException.invalidState("Document " + document.getDocNo()
                    + " is no longer a draft and cannot be deleted");
        }
        if (document.getDocType() == PurchaseDocType.RECEIPT) {
            throw ApiException.invalidState("A goods receipt cannot be deleted; reverse it instead");
        }
        documents.delete(document);
        audit.record("purchase." + document.getDocType().name().toLowerCase() + ".delete",
                CurrentUser.id().orElse(null), CurrentUser.username().orElse(null),
                "pur_document", String.valueOf(id), Map.of("docNo", document.getDocNo()));
    }

    // ------------------------------------------------------------- life cycle

    /**
     * Hands a document over for approval.
     *
     * <p>Below the threshold it is approved there and then by whoever submitted it,
     * with a note saying so. Above it, it waits.
     */
    @Transactional
    public PurchaseDtos.DocumentView submit(Long id) {
        PurchaseDocument document = require(id);
        if (document.getStatus() != DocumentLifecycle.DRAFT) {
            throw ApiException.invalidState("Only a draft can be submitted");
        }
        String operator = CurrentUser.username().orElse("system");
        document.submit(operator);
        if (document.getTotalAmount().compareTo(approvalThreshold) < 0) {
            document.approve(operator, "Auto-approved: below the approval threshold of "
                    + approvalThreshold.stripTrailingZeros().toPlainString());
        }
        documents.save(document);
        audit.record("purchase.submit", CurrentUser.id().orElse(null), operator,
                "pur_document", String.valueOf(id),
                Map.of("docNo", document.getDocNo(), "status", document.getStatus().name()));
        return views(List.of(document)).get(0);
    }

    @Transactional
    public PurchaseDtos.DocumentView approve(Long id, String remark) {
        PurchaseDocument document = require(id);
        if (document.getStatus() != DocumentLifecycle.SUBMITTED) {
            throw ApiException.invalidState("Only a submitted document can be approved; "
                    + document.getDocNo() + " is " + document.getStatus().name());
        }
        document.approve(CurrentUser.username().orElse("system"), blankToNull(remark));
        documents.save(document);
        audit.record("purchase.approve", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "pur_document", String.valueOf(id),
                Map.of("docNo", document.getDocNo()));
        return views(List.of(document)).get(0);
    }

    @Transactional
    public PurchaseDtos.DocumentView reject(Long id, String remark) {
        PurchaseDocument document = require(id);
        if (document.getStatus() != DocumentLifecycle.SUBMITTED) {
            throw ApiException.invalidState("Only a submitted document can be rejected; "
                    + document.getDocNo() + " is " + document.getStatus().name());
        }
        document.reject(CurrentUser.username().orElse("system"), blankToNull(remark));
        documents.save(document);
        audit.record("purchase.reject", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "pur_document", String.valueOf(id),
                Map.of("docNo", document.getDocNo()));
        return views(List.of(document)).get(0);
    }

    @Transactional
    public PurchaseDtos.DocumentView cancel(Long id) {
        PurchaseDocument document = require(id);
        if (!document.getStatus().isOpen()) {
            throw ApiException.invalidState("Document " + document.getDocNo()
                    + " is " + document.getStatus().name() + " and cannot be cancelled");
        }
        if (document.getDocType() == PurchaseDocType.RECEIPT) {
            throw ApiException.invalidState("A goods receipt cannot be cancelled; reverse it instead");
        }
        document.setStatus(DocumentLifecycle.CANCELLED);
        documents.save(document);
        audit.record("purchase.cancel", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "pur_document", String.valueOf(id),
                Map.of("docNo", document.getDocNo()));
        return views(List.of(document)).get(0);
    }

    /** Shuts a document that will never be carried out in full. */
    @Transactional
    public PurchaseDtos.DocumentView close(Long id) {
        PurchaseDocument document = require(id);
        if (document.getStatus().isTerminal()) {
            throw ApiException.invalidState("Document " + document.getDocNo()
                    + " is already " + document.getStatus().name());
        }
        document.setStatus(DocumentLifecycle.CLOSED);
        documents.save(document);
        audit.record("purchase.close", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "pur_document", String.valueOf(id),
                Map.of("docNo", document.getDocNo()));
        return views(List.of(document)).get(0);
    }

    // ------------------------------------------------------------------- flow

    /**
     * Turns an approved requisition into a purchase order.
     *
     * <p>The order lines remember the requisition lines they came from, and the
     * requisition records how much of each line has been ordered. That is what stops
     * the same request being ordered twice, which is the failure nobody notices until
     * twice as much material arrives as anybody asked for.
     */
    @Transactional
    public PurchaseDtos.DocumentView convertToOrder(Long requisitionId,
                                                    PurchaseDtos.ConvertRequest request) {
        PurchaseDocument requisition = require(requisitionId);
        if (requisition.getDocType() != PurchaseDocType.REQUISITION) {
            throw ApiException.badRequest("Only a requisition can be converted to an order");
        }
        if (requisition.getStatus() != DocumentLifecycle.APPROVED) {
            throw ApiException.invalidState("Requisition " + requisition.getDocNo()
                    + " is " + requisition.getStatus().name()
                    + "; approve it before converting");
        }
        Partner partner = request.partnerId() == null
                ? (requisition.getPartnerId() == null ? null : requirePartner(requisition.getPartnerId()))
                : requirePartner(request.partnerId());
        if (partner == null) {
            throw ApiException.badRequest("An order needs a supplier: name one on the request, "
                    + "or on the requisition before converting it");
        }
        if (!partner.getPartnerType().tradesAsSupplier()) {
            throw ApiException.badRequest("Partner " + partner.getCode() + " is not a supplier");
        }
        LocalDate bizDate = request.bizDate() == null ? LocalDate.now() : request.bizDate();

        List<PurchaseDocumentLine> picks = new ArrayList<>();
        for (PurchaseDocumentLine line : requisition.getLines()) {
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
                        + requisition.getDocNo());
            }
            PurchaseDocumentLine copy = new PurchaseDocumentLine(
                    (picks.size() + 1) * 10, line.getItemId(), take, line.getUnitPrice(),
                    line.getTaxRate(), line.getRemark());
            copy.setSourceLineId(line.getId());
            picks.add(copy);
        }
        if (picks.isEmpty()) {
            throw ApiException.badRequest("Requisition " + requisition.getDocNo()
                    + " has nothing left to order");
        }

        PurchaseDocument order = new PurchaseDocument(
                numbers.next(PurchaseDocType.ORDER.prefix(), bizDate),
                PurchaseDocType.ORDER, null, bizDate);
        order.setPartnerId(partner.getId());
        order.setPaymentTermDays(partner.getPaymentTermsDays());
        order.setSourceType("PUR_REQUISITION");
        order.setSourceId(requisition.getDocNo());
        order.setRemark(blankToNull(request.remark()));
        order.replaceLines(picks);
        order.recalculate();
        documents.save(order);

        // Only now does the requisition part with the quantity.
        for (PurchaseDocumentLine pick : picks) {
            PurchaseDocumentLine source = requisition.getLines().stream()
                    .filter(line -> line.getId().equals(pick.getSourceLineId()))
                    .findFirst().orElseThrow();
            source.fulfil(pick.getQty());
        }
        requisition.setStatus(requisition.isFulfilled()
                ? DocumentLifecycle.COMPLETED : DocumentLifecycle.PARTIAL);
        documents.save(requisition);

        audit.record("purchase.convert", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "pur_document", String.valueOf(order.getId()),
                Map.of("order", order.getDocNo(), "from", requisition.getDocNo()));
        return views(List.of(order)).get(0);
    }

    /**
     * Records goods arriving against an order, as a draft receipt.
     *
     * <p>A draft, because somebody has to look at what turned up before it becomes the
     * book. Posting it is a separate act.
     */
    @Transactional
    public PurchaseDtos.DocumentView receive(Long orderId, PurchaseDtos.ReceiveRequest request) {
        PurchaseDocument order = require(orderId);
        if (order.getDocType() != PurchaseDocType.ORDER) {
            throw ApiException.badRequest("Goods can only be received against a purchase order");
        }
        if (order.getStatus() != DocumentLifecycle.APPROVED
                && order.getStatus() != DocumentLifecycle.PARTIAL) {
            throw ApiException.invalidState("Order " + order.getDocNo() + " is "
                    + order.getStatus().name() + "; approve it before receiving against it");
        }
        Warehouse warehouse = requireUsableWarehouse(request.warehouseId());
        if (request.locationId() != null) {
            requireLocation(request.locationId(), warehouse.getId());
        }

        List<PurchaseDocumentLine> lines = new ArrayList<>();
        for (PurchaseDtos.ReceiveLine pick : request.lines()) {
            PurchaseDocumentLine source = order.getLines().stream()
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
            PurchaseDocumentLine line = new PurchaseDocumentLine(
                    (lines.size() + 1) * 10, source.getItemId(), pick.qty(),
                    source.getUnitPrice(), source.getTaxRate(), source.getRemark());
            line.setSourceLineId(source.getId());
            line.setWarehouseId(warehouse.getId());
            line.setLocationId(request.locationId());
            line.setLotId(pick.lotId());
            lines.add(line);
        }

        PurchaseDocument receipt = new PurchaseDocument(
                numbers.next(PurchaseDocType.RECEIPT.prefix(), request.bizDate()),
                PurchaseDocType.RECEIPT, warehouse.getId(), request.bizDate());
        receipt.setLocationId(request.locationId());
        receipt.setPartnerId(order.getPartnerId());
        receipt.setPaymentTermDays(order.getPaymentTermDays());
        receipt.setSourceType("PUR_ORDER");
        receipt.setSourceId(order.getDocNo());
        receipt.setRemark(blankToNull(request.remark()));
        receipt.replaceLines(lines);
        receipt.recalculate();
        documents.save(receipt);

        audit.record("purchase.receive", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "pur_document", String.valueOf(receipt.getId()),
                Map.of("receipt", receipt.getDocNo(), "order", order.getDocNo()));
        return views(List.of(receipt)).get(0);
    }

    /**
     * Posts a receipt: stock arrives, the debt appears, the order records it.
     *
     * <p>The value that enters stock is the net price. That is deliberate: VAT is money
     * the tax office is owed, not part of what the material cost us, and carrying it
     * into the average cost would inflate every issue priced afterwards.
     */
    @Transactional
    public PurchaseDtos.DocumentView post(Long receiptId) {
        PurchaseDocument receipt = require(receiptId);
        if (receipt.getDocType() != PurchaseDocType.RECEIPT) {
            throw ApiException.badRequest("Only a goods receipt can be posted");
        }
        if (receipt.getStatus() != DocumentLifecycle.DRAFT) {
            throw ApiException.invalidState("Receipt " + receipt.getDocNo() + " is "
                    + receipt.getStatus().name() + " and cannot be posted");
        }
        if (receipt.getPartnerId() == null) {
            throw ApiException.badRequest("Receipt " + receipt.getDocNo()
                    + " has no supplier, so no payable can be raised");
        }
        Warehouse warehouse = requireUsableWarehouse(receipt.getWarehouseId());

        List<DocumentDtos.LineRequest> stockLines = receipt.getLines().stream()
                .map(line -> new DocumentDtos.LineRequest(
                        line.getItemId(), line.getLotId(), line.getQty(), line.getUnitPrice(),
                        null, line.getRemark()))
                .toList();
        DocumentDtos.DocumentRequest stockRequest = new DocumentDtos.DocumentRequest(
                DocumentType.INBOUND, warehouse.getId(), receipt.getLocationId(), null, null,
                receipt.getPartnerId(), receipt.getBizDate(),
                "From purchase receipt " + receipt.getDocNo(), stockLines);
        stockDocuments.postSourced(stockRequest, SOURCE_TYPE, receipt.getDocNo());

        LocalDate dueDate = receipt.getBizDate().plusDays(Math.max(receipt.getPaymentTermDays(), 0));
        payables.createFrom(SOURCE_TYPE, receipt.getId(), receipt.getDocNo(),
                receipt.getPartnerId(), receipt.getBizDate(), dueDate,
                receipt.getNetAmount(), receipt.getTaxAmount(), receipt.getTotalAmount(),
                "Purchase receipt " + receipt.getDocNo());

        applyFulfilment(receipt, true);
        receipt.post(CurrentUser.username().orElse("system"));
        documents.save(receipt);

        audit.record("purchase.receipt.post", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "pur_document", String.valueOf(receipt.getId()),
                Map.of("docNo", receipt.getDocNo(),
                        "amount", receipt.getTotalAmount().toPlainString()));
        return views(List.of(receipt)).get(0);
    }

    /**
     * Undoes a posted receipt.
     *
     * <p>The stock documents it created are reversed rather than deleted, the payable is
     * voided rather than removed, and the order gives the quantity back so it can be
     * received again. Nothing is erased: what is on the books afterwards is the receipt,
     * and the correction beside it.
     */
    @Transactional
    public PurchaseDtos.DocumentView reverse(Long receiptId) {
        PurchaseDocument receipt = require(receiptId);
        if (receipt.getDocType() != PurchaseDocType.RECEIPT) {
            throw ApiException.badRequest("Only a goods receipt can be reversed");
        }
        if (receipt.getStatus() != DocumentLifecycle.POSTED) {
            throw ApiException.invalidState("Only a posted receipt can be reversed; "
                    + receipt.getDocNo() + " is " + receipt.getStatus().name());
        }
        stockDocuments.reverseSourced(SOURCE_TYPE, receipt.getDocNo());
        payables.voidSource(SOURCE_TYPE, receiptId);
        applyFulfilment(receipt, false);
        receipt.markReversed();
        documents.save(receipt);

        audit.record("purchase.receipt.reverse", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "pur_document", String.valueOf(receipt.getId()),
                Map.of("docNo", receipt.getDocNo()));
        return views(List.of(receipt)).get(0);
    }

    /** Moves fulfilled quantities between a receipt and the order it came from. */
    private void applyFulfilment(PurchaseDocument receipt, boolean forward) {
        String orderNo = receipt.getSourceId();
        if (orderNo == null) {
            return;
        }
        PurchaseDocument order = documents.findByDocNo(orderNo).orElse(null);
        if (order == null) {
            return;
        }
        for (PurchaseDocumentLine line : receipt.getLines()) {
            PurchaseDocumentLine source = order.getLines().stream()
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

    // --------------------------------------------------------------- helpers

    private void apply(PurchaseDocument document,
                       PurchaseDtos.DocumentRequest request,
                       Partner partner) {
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

    private List<PurchaseDocumentLine> buildLines(List<PurchaseDtos.LineRequest> requests) {
        List<PurchaseDocumentLine> lines = new ArrayList<>();
        int index = 0;
        for (PurchaseDtos.LineRequest line : requests) {
            if (!items.existsById(line.itemId())) {
                throw ApiException.badRequest("Item " + line.itemId() + " does not exist");
            }
            BigDecimal taxRate = line.taxRate() == null ? defaultTaxRate : line.taxRate();
            PurchaseDocumentLine built = new PurchaseDocumentLine(
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

    private Partner resolvePartner(PurchaseDocType type, Long partnerId) {
        if (partnerId == null) {
            if (type.needsPartner()) {
                throw ApiException.badRequest("A " + type.name().toLowerCase() + " needs a supplier");
            }
            return null;
        }
        Partner partner = requirePartner(partnerId);
        if (type.needsPartner() && !partner.getPartnerType().tradesAsSupplier()) {
            throw ApiException.badRequest("Partner " + partner.getCode() + " is not a supplier");
        }
        return partner;
    }

    private Warehouse resolveWarehouse(PurchaseDocType type, Long warehouseId) {
        if (warehouseId == null) {
            if (type.needsWarehouse()) {
                throw ApiException.badRequest("A goods receipt needs the warehouse the goods went into");
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

    /** How much of a line a conversion or receipt is taking; defaults to everything open. */
    private static BigDecimal quantityFor(List<PurchaseDtos.PickLine> picks,
                                          Long lineId,
                                          BigDecimal open) {
        if (picks == null || picks.isEmpty()) {
            return open;
        }
        return picks.stream()
                .filter(pick -> pick.lineId().equals(lineId))
                .findFirst()
                .map(PurchaseDtos.PickLine::qty)
                .orElse(open);
    }

    private String itemCode(Long itemId) {
        return items.findById(itemId).map(Item::getCode).orElse("item " + itemId);
    }

    private PurchaseDocument require(Long id) {
        return documents.findWithLinesById(id)
                .orElseThrow(() -> ApiException.notFound("Purchase document " + id + " does not exist"));
    }

    private Specification<PurchaseDocument> specification(PurchaseDocType type,
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

    List<PurchaseDtos.DocumentView> views(List<PurchaseDocument> source) {
        if (source.isEmpty()) {
            return List.of();
        }
        Map<Long, Partner> partnerById = partners
                .findAllById(source.stream().map(PurchaseDocument::getPartnerId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Partner::getId, Function.identity()));
        Map<Long, Warehouse> warehouseById = warehouses
                .findAllById(source.stream().map(PurchaseDocument::getWarehouseId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        Map<Long, Location> locationById = locations
                .findAllById(source.stream().map(PurchaseDocument::getLocationId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Location::getId, Function.identity()));
        Map<Long, Item> itemById = items
                .findAllById(source.stream().flatMap(document -> document.getLines().stream())
                        .map(PurchaseDocumentLine::getItemId).distinct().toList())
                .stream().collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<Long, Lot> lotById = lots
                .findAllById(source.stream().flatMap(document -> document.getLines().stream())
                        .map(PurchaseDocumentLine::getLotId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Lot::getId, Function.identity()));
        Map<Long, String> docNoById = documents
                .findAllById(source.stream().map(PurchaseDocument::getReversalOf)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(PurchaseDocument::getId, PurchaseDocument::getDocNo));

        return source.stream().map(document -> {
            Partner partner = partnerById.get(document.getPartnerId());
            Warehouse warehouse = warehouseById.get(document.getWarehouseId());
            Location location = locationById.get(document.getLocationId());
            BigDecimal totalQty = document.getLines().stream()
                    .map(PurchaseDocumentLine::getQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new PurchaseDtos.DocumentView(
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
                        return new PurchaseDtos.LineView(
                                line.getId(), line.getLineNo(), line.getItemId(),
                                item == null ? null : item.getCode(),
                                item == null ? null : item.getName(),
                                item == null ? null : item.getUomCode(),
                                line.getQty(), line.getUnitPrice(), line.getTaxRate(),
                                line.getNetAmount(), line.getTaxAmount(), line.getTotalAmount(),
                                line.getQtyFulfilled(), line.openQty(),
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
