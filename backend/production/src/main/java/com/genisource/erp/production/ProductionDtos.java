package com.genisource.erp.production;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProductionDtos {

    private ProductionDtos() {
    }

    public record LineRequest(
            @NotNull(message = "{validation.item.required}")
            Long itemId,

            @NotNull(message = "{validation.qty.required}")
            @DecimalMin(value = "0.000001", message = "{validation.qty.positive}")
            BigDecimal qtyRequired,

            /** Where this component is drawn from; null falls back to the order's warehouse. */
            Long warehouseId,

            @Size(max = 255, message = "{validation.remarkLine.size}")
            String remark) {
    }

    /**
     * Raising or editing a works order.
     *
     * @param bomId  which revision to freeze; null takes the released one for the item
     * @param lines  components to consume; empty expands the BOM for {@code qtyPlanned}
     */
    public record OrderRequest(
            @NotNull(message = "{validation.item.required}")
            Long itemId,

            @NotNull(message = "{validation.qty.required}")
            @DecimalMin(value = "0.000001", message = "{validation.qty.positive}")
            BigDecimal qtyPlanned,

            Long bomId,
            Long warehouseId,
            Long locationId,
            LocalDate plannedStart,
            LocalDate plannedEnd,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark,

            List<@Valid LineRequest> lines) {
    }

    /** One component being taken to the line. */
    public record IssueLine(
            @NotNull(message = "{validation.line.required}")
            Long orderLineId,

            @NotNull(message = "{validation.qty.required}")
            @DecimalMin(value = "0.000001", message = "{validation.qty.positive}")
            BigDecimal qty,

            Long lotId) {
    }

    public record IssueRequest(
            @NotNull(message = "{validation.bizDate.required}")
            LocalDate bizDate,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark,

            @NotEmpty(message = "{validation.lines.required}")
            List<@Valid IssueLine> lines) {
    }

    /** Booking finished goods in. */
    public record CompleteRequest(
            @NotNull(message = "{validation.qty.required}")
            @DecimalMin(value = "0.000001", message = "{validation.qty.positive}")
            BigDecimal qty,

            @NotNull(message = "{validation.warehouse.required}")
            Long warehouseId,

            Long locationId,

            @NotNull(message = "{validation.bizDate.required}")
            LocalDate bizDate,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark) {
    }

    public record LineView(
            Long id,
            Integer lineNo,
            Long itemId,
            String itemCode,
            String itemName,
            String uomCode,
            BigDecimal qtyRequired,
            BigDecimal qtyIssued,
            BigDecimal qtyLocked,
            BigDecimal openQty,
            Long warehouseId,
            String warehouseCode,
            String remark) {
    }

    public record OperationView(
            Integer sequence,
            String name,
            Long workCenterId,
            String workCenterCode,
            BigDecimal setupMinutes,
            BigDecimal runMinutes,
            OperationStatus status,
            Instant completedAt) {
    }

    public record OrderView(
            Long id,
            String docNo,
            Long itemId,
            String itemCode,
            String itemName,
            String uomCode,
            Long bomId,
            String bomVersion,
            BigDecimal qtyPlanned,
            BigDecimal qtyCompleted,
            BigDecimal remainingQty,
            ProductionOrderStatus status,
            Long warehouseId,
            String warehouseCode,
            Long locationId,
            String locationName,
            LocalDate plannedStart,
            LocalDate plannedEnd,
            Instant actualStart,
            Instant actualEnd,
            String mesWoNo,
            String sourceType,
            String sourceId,
            String remark,
            long rowVersion,
            List<LineView> lines,
            List<OperationView> operations) {
    }
}
