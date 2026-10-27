package com.genisource.erp.inventory.stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class StockDtos {

    private StockDtos() {
    }

    /**
     * One balance line.
     *
     * <p>{@code available} is the number a picker actually cares about: on hand minus
     * what is promised and what is held.
     */
    public record StockView(
            Long id,
            Long itemId,
            String itemCode,
            String itemName,
            String uomCode,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            Long locationId,
            String locationCode,
            String locationName,
            Long lotId,
            String lotNo,
            String lotType,
            BigDecimal onHand,
            BigDecimal reserved,
            BigDecimal inTransit,
            BigDecimal locked,
            BigDecimal available,
            BigDecimal avgCost,
            BigDecimal totalAmount,
            Instant updatedAt) {
    }

    /** Stock of one item across every place it sits. */
    public record StockSummary(
            Long itemId,
            String itemCode,
            String itemName,
            String uomCode,
            BigDecimal onHand,
            BigDecimal reserved,
            BigDecimal locked,
            BigDecimal inTransit,
            BigDecimal available,
            BigDecimal totalAmount,
            int locationCount,
            List<StockView> lines) {
    }
}
