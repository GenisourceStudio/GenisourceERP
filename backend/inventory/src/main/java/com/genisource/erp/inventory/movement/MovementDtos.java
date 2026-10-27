package com.genisource.erp.inventory.movement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class MovementDtos {

    private MovementDtos() {
    }

    public record MovementView(
            Long id,
            String docNo,
            MovementType movementType,
            String direction,
            Long itemId,
            String itemCode,
            String itemName,
            String uomCode,
            Long lotId,
            String lotNo,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            Long locationId,
            String locationName,
            BigDecimal qty,
            BigDecimal unitCost,
            BigDecimal amount,
            BigDecimal balanceQtyAfter,
            BigDecimal balanceAmountAfter,
            String operator,
            String remark,
            Instant movedAt) {
    }

    /** The full story of one batch: every movement it ever had, and what is left. */
    public record LotTrace(
            Long lotId,
            String lotNo,
            Long itemId,
            String itemCode,
            String itemName,
            String lotType,
            BigDecimal onHand,
            BigDecimal totalAmount,
            LocalDate productionDate,
            LocalDate expiryDate,
            List<MovementView> movements) {
    }
}
