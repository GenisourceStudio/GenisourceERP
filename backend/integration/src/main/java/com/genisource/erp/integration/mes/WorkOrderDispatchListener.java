package com.genisource.erp.integration.mes;

import com.genisource.erp.platform.production.ProductionReleaseListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends a released works order to MES.
 *
 * <p>The order is not pushed straight to MES: it is written to the outbox, where a
 * worker delivers it. A release must not fail because the network to another system
 * is down — the work is released either way, and the message catches up.
 */
@Component
public class WorkOrderDispatchListener implements ProductionReleaseListener {

    private final MesOutboxService outbox;

    public WorkOrderDispatchListener(MesOutboxService outbox) {
        this.outbox = outbox;
    }

    @Override
    public void onReleased(Long orderId, String orderNo, String itemCode, String itemName,
                           BigDecimal plannedQty, String uom, LocalDate plannedStart,
                           LocalDate plannedEnd, String bomVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("erpProductionOrderId", String.valueOf(orderId));
        payload.put("orderNo", orderNo);
        payload.put("itemCode", itemCode);
        payload.put("itemName", itemName);
        // Strip the trailing zeros a scale of six leaves behind: "2" reads as two
        // pieces, "2.000000" reads as a precision the number does not have.
        payload.put("plannedQty", plannedQty == null ? null
                : plannedQty.stripTrailingZeros().toPlainString());
        payload.put("uom", uom);
        payload.put("planStart", plannedStart == null ? null : plannedStart.toString());
        payload.put("planEnd", plannedEnd == null ? null : plannedEnd.toString());
        payload.put("bomVersion", bomVersion);
        outbox.enqueue(MesOutbox.MessageType.WORK_ORDER, orderNo, "erp-po-" + orderId, payload);
    }
}
