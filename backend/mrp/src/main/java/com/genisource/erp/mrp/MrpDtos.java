package com.genisource.erp.mrp;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class MrpDtos {

    private MrpDtos() {
    }

    /**
     * What to ask the engine.
     *
     * <p>Every field is optional: a planner who just wants "what do I need for the next
     * month" posts an empty body and gets the defaults.
     *
     * @param horizonDays  how far ahead to look, when {@code demandTo} is not given
     * @param includeSafety whether safety stock counts as demand — it normally should, but
     *                      a planner chasing a cash-flow problem may want to see the
     *                      position without it
     * @param warehouseId  where generated works orders will draw material from; null leaves
     *                     them without one, and release will ask for it
     */
    public record RunRequest(
            @Min(value = 1, message = "{validation.horizon.min}")
            Integer horizonDays,

            LocalDate demandFrom,
            LocalDate demandTo,
            Boolean includeSafety,

            @Min(value = 1, message = "{validation.levels.min}")
            Integer maxLevels,

            Long warehouseId,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark) {
    }

    public record RunView(
            Long id,
            String runNo,
            String status,
            Integer horizonDays,
            LocalDate demandFrom,
            LocalDate demandTo,
            boolean includeSafety,
            Integer maxLevels,
            Integer itemCount,
            Integer supplyCount,
            Integer shortageCount,
            String runBy,
            Instant runAt,
            String remark) {
    }

    public record DemandView(
            Long id,
            Long itemId,
            String itemCode,
            String itemName,
            String uomCode,
            Integer itemLevel,
            BigDecimal grossQty,
            BigDecimal onHand,
            BigDecimal reservedQty,
            BigDecimal lockedQty,
            BigDecimal inTransit,
            BigDecimal safetyStock,
            BigDecimal netQty,
            LocalDate requiredDate,
            BigDecimal fromSales,
            BigDecimal fromComponents,
            boolean shortage) {
    }

    public record PlanView(
            Long id,
            Long itemId,
            String itemCode,
            String itemName,
            String uomCode,
            MrpSupplyType supplyType,
            BigDecimal qty,
            BigDecimal netQty,
            BigDecimal orderMultiple,
            Long supplierId,
            String supplierName,
            Integer leadTimeDays,
            LocalDate startDate,
            LocalDate dueDate,
            MrpPlanStatus status,
            String convertedType,
            String convertedId,
            String convertedNo,
            String remark) {
    }

    /** A run with everything behind it: the evidence, then the advice. */
    public record RunDetail(
            RunView run,
            List<DemandView> demands,
            List<PlanView> plans) {
    }

    /**
     * Turning advice into an order.
     *
     * @param qty         override the recommended quantity; null takes the plan's
     * @param supplierId  override the item's default supplier
     * @param unitPrice   what purchasing thinks it costs; null leaves the line at zero for
     *                    the buyer to fill, because a plan does not know today's price
     * @param warehouseId where a generated works order draws material from; ignored when
     *                    converting to a purchase order
     */
    public record ConvertRequest(
            @DecimalMin(value = "0.000001", message = "{validation.qty.positive}")
            BigDecimal qty,

            Long supplierId,

            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal unitPrice,

            LocalDate bizDate,

            Long warehouseId,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark) {
    }

    /** Dropping a plan row, with the reason somebody decided to. */
    public record IgnoreRequest(
            @Size(max = 255, message = "{validation.remarkLine.size}")
            String remark) {
    }
}
