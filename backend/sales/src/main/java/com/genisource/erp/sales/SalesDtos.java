package com.genisource.erp.sales;

import com.genisource.erp.platform.common.DocumentLifecycle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SalesDtos {

    private SalesDtos() {
    }

    public record LineRequest(
            @NotNull(message = "{validation.item.required}")
            Long itemId,

            @NotNull(message = "{validation.qty.required}")
            @DecimalMin(value = "0.000001", message = "{validation.qty.positive}")
            BigDecimal qty,

            /** Price excluding tax; null falls back to the configured default rate's net price. */
            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal unitPrice,

            /** VAT as a fraction: 0.13 means thirteen percent. Null takes the default. */
            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal taxRate,

            Long lotId,

            @Size(max = 255, message = "{validation.remarkLine.size}")
            String remark) {
    }

    public record DocumentRequest(
            Long partnerId,
            Long warehouseId,
            Long locationId,

            @NotNull(message = "{validation.bizDate.required}")
            LocalDate bizDate,

            LocalDate requiredDate,

            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            Integer paymentTermDays,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark,

            @NotEmpty(message = "{validation.lines.required}")
            List<@Valid LineRequest> lines) {
    }

    public record LineView(
            Long id,
            Integer lineNo,
            Long itemId,
            String itemCode,
            String itemName,
            String uomCode,
            BigDecimal qty,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            BigDecimal netAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            BigDecimal qtyFulfilled,
            BigDecimal qtyReserved,
            BigDecimal openQty,
            Long lotId,
            String lotNo,
            Long sourceLineId,
            String remark) {
    }

    public record DocumentView(
            Long id,
            String docNo,
            SalesDocType docType,
            DocumentLifecycle status,
            Long partnerId,
            String partnerCode,
            String partnerName,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            Long locationId,
            String locationName,
            LocalDate bizDate,
            LocalDate requiredDate,
            Integer paymentTermDays,
            BigDecimal netAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            BigDecimal totalQty,
            boolean stockReserved,
            String sourceType,
            String sourceId,
            String submittedBy,
            String approvedBy,
            Instant approvedAt,
            String approvalRemark,
            Instant postedAt,
            Long reversalOf,
            String reversalOfNo,
            String remark,
            long rowVersion,
            List<LineView> lines) {
    }

    /** What an approver says when approving or turning a document down. */
    public record ApprovalRequest(
            @Size(max = 255, message = "{validation.remarkLine.size}")
            String remark) {
    }

    /**
     * Turning a requisition into an order.
     *
     * @param lines which requisition lines to take and how much of each; empty means
     *              everything still open
     */
    public record ConvertRequest(
            Long partnerId,
            LocalDate bizDate,
            @Size(max = 500, message = "{validation.remark.size}")
            String remark,
            List<@Valid PickLine> lines) {
    }

    public record PickLine(
            @NotNull(message = "{validation.line.required}")
            Long lineId,

            @NotNull(message = "{validation.qty.required}")
            @DecimalMin(value = "0.000001", message = "{validation.qty.positive}")
            BigDecimal qty) {
    }

    /** Goods leaving against an order. */
    public record ShipRequest(
            @NotNull(message = "{validation.warehouse.required}")
            Long warehouseId,

            Long locationId,

            @NotNull(message = "{validation.bizDate.required}")
            LocalDate bizDate,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark,

            @NotEmpty(message = "{validation.lines.required}")
            List<@Valid ShipLine> lines) {
    }

    public record ShipLine(
            @NotNull(message = "{validation.line.required}")
            Long orderLineId,

            @NotNull(message = "{validation.qty.required}")
            @DecimalMin(value = "0.000001", message = "{validation.qty.positive}")
            BigDecimal qty,

            Long lotId) {
    }
}
