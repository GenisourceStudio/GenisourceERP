package com.genisource.erp.integration.mes;

import com.genisource.erp.production.ProductionDtos;
import com.genisource.erp.production.ProductionOrder;
import com.genisource.erp.production.ProductionOrderRepository;
import com.genisource.erp.production.ProductionService;
import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * What MES tells us, turned into what the ERP does.
 *
 * <p>Two kinds of news arrive: progress (the order is half done) and completion (the
 * order is finished, here is how many good ones and how many scrap).
 *
 * <p>A completion books stock through the production module, not through a second
 * path of its own — the same completion that a shop-floor supervisor would enter by
 * hand. One way to finish an order means one set of numbers, whichever door it came
 * through.
 */
@Service
public class MesInboundService {

    private static final Logger log = LoggerFactory.getLogger(MesInboundService.class);

    private final MesCompletionRepository completions;
    private final MesOutboxRepository outboxRepository;
    private final ProductionOrderRepository orders;
    private final com.genisource.erp.inventory.warehouse.WarehouseRepository warehouses;
    private final ProductionService production;
    private final AuditService audit;

    public MesInboundService(MesCompletionRepository completions,
                             MesOutboxRepository outboxRepository,
                             ProductionOrderRepository orders,
                             com.genisource.erp.inventory.warehouse.WarehouseRepository warehouses,
                             ProductionService production, AuditService audit) {
        this.completions = completions;
        this.outboxRepository = outboxRepository;
        this.orders = orders;
        this.warehouses = warehouses;
        this.production = production;
        this.audit = audit;
    }

    /**
     * Books a completion MES reported.
     *
     * <p>Idempotent: the same {@code mesCompletionId} answers with the first result
     * and books nothing twice. MES retries, networks retry, and a batch of finished
     * goods booked twice is a stock figure nobody can believe.
     */
    @Transactional
    public MesDtos.CompletionResponse complete(MesDtos.CompletionRequest request) {
        MesCompletion existing = completions.findByMesCompletionId(request.mesCompletionId())
                .orElse(null);
        if (existing != null) {
            return new MesDtos.CompletionResponse(existing.getMesCompletionId(), true,
                    existing.getProductionOrderId(),
                    "Already booked; nothing was done a second time");
        }

        ProductionOrder order = orders.findByMesWoNo(request.mesWorkOrderNo())
                .orElseThrow(() -> ApiException.notFound(
                        "MES reported work order " + request.mesWorkOrderNo()
                                + ", which this ERP does not know"));
        BigDecimal completed = request.completedQty();
        if (completed == null || completed.signum() <= 0) {
            throw ApiException.badRequest("A completion must say how many were completed");
        }
        BigDecimal remaining = order.getQtyPlanned().subtract(order.getQtyCompleted());
        if (completed.compareTo(remaining) > 0) {
            // MES over-reported. The shop floor should have caught this; refusing is
            // the only answer that does not put more into stock than was planned.
            throw new MesUnprocessableException("MES reported " + completed.toPlainString()
                    + " completed on " + order.getDocNo() + ", but only "
                    + remaining.toPlainString() + " were planned");
        }
        Long warehouseId = receiptWarehouse(order, request.warehouseCode());

        ProductionDtos.OrderView view = production.complete(order.getId(),
                new ProductionDtos.CompleteRequest(
                        completed, warehouseId, null,
                        request.completedAt() == null ? LocalDate.now()
                                : LocalDate.ofInstant(request.completedAt(),
                                        java.time.ZoneId.systemDefault()),
                        "Completed by MES " + request.mesWorkOrderNo()));

        completions.save(new MesCompletion(request.mesCompletionId(), request.mesWorkOrderNo(),
                order.getId(), completed,
                request.scrapQty() == null ? BigDecimal.ZERO : request.scrapQty()));
        audit.record("integration.mes.completion", CurrentUser.id().orElse(null),
                CurrentUser.username().orElse(null), "prd_production_order",
                String.valueOf(order.getId()),
                Map.of("mesWorkOrderNo", request.mesWorkOrderNo(),
                        "completedQty", completed.toPlainString()));
        log.info("MES completed {} with {} good units", request.mesWorkOrderNo(), completed);
        return new MesDtos.CompletionResponse(request.mesCompletionId(), false, order.getId(),
                "Booked " + view.docNo());
    }

    /**
     * Where the finished goods land.
     *
     * <p>MES names the warehouse when it reports; when it does not, the order's own
     * warehouse is used. A name nobody recognises is refused rather than guessed at:
     * goods booked into the wrong warehouse are a stock figure nobody can trust.
     */
    private Long receiptWarehouse(ProductionOrder order, String warehouseCode) {
        if (warehouseCode == null || warehouseCode.isBlank()) {
            if (order.getWarehouseId() == null) {
                throw new MesUnprocessableException("Order " + order.getDocNo()
                        + " has no warehouse, so a completion cannot be booked");
            }
            return order.getWarehouseId();
        }
        return warehouses.findByCode(warehouseCode.trim())
                .map(com.genisource.erp.inventory.warehouse.Warehouse::getId)
                .orElseThrow(() -> new MesUnprocessableException(
                        "MES reported warehouse " + warehouseCode + ", which this ERP does not know"));
    }

    /** Records how far MES says the order has got. Progress books nothing. */
    @Transactional
    public MesDtos.ProgressResponse progress(MesDtos.ProgressRequest request) {
        ProductionOrder order = orders.findByMesWoNo(request.mesWorkOrderNo())
                .orElseThrow(() -> ApiException.notFound(
                        "MES reported work order " + request.mesWorkOrderNo()
                                + ", which this ERP does not know"));
        BigDecimal done = request.completedQty() == null ? BigDecimal.ZERO
                : request.completedQty();
        order.setMesProgressQty(done);
        orders.save(order);
        return new MesDtos.ProgressResponse(request.mesWorkOrderNo(), done);
    }

    /** The orders MES knows about, for the integration screen. */
    @Transactional(readOnly = true)
    public List<MesDtos.OutboxView> outbox() {
        return outboxRepository.findAll().stream()
                .map(message -> new MesDtos.OutboxView(
                        message.getId(), message.getMessageType().name(),
                        message.getSubject(), message.getStatus().name(),
                        message.getAttempts(), message.getLastError(),
                        message.getNextAttemptAt(), message.getSentAt()))
                .toList();
    }
}
