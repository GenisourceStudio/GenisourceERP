package com.genisource.erp.inventory.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentRequest(
            @NotNull(message = "{validation.docType.required}")
            DocumentType docType,

            @NotNull(message = "{validation.warehouse.required}")
            Long warehouseId,

            Long locationId,

            Long toWarehouseId,

            Long toLocationId,

            Long partnerId,

            @NotNull(message = "{validation.bizDate.required}")
            LocalDate bizDate,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark,

            @NotEmpty(message = "{validation.lines.required}")
            List<@Valid LineRequest> lines) {
    }

    public record LineRequest(
            @NotNull(message = "{validation.item.required}")
            Long itemId,

            Long lotId,

            /**
             * Quantity on a receipt or an issue. On a count this is the expected quantity
             * from the book, and {@code countedQty} holds what was found on the shelf.
             */
            @NotNull(message = "{validation.qty.required}")
            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal qty,

            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal unitCost,

            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal countedQty,

            @Size(max = 255)
            String remark) {
    }

    public record DocumentView(
            Long id,
            String docNo,
            DocumentType docType,
            DocumentStatus status,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            Long locationId,
            String locationName,
            Long toWarehouseId,
            String toWarehouseName,
            Long toLocationId,
            String toLocationName,
            Long partnerId,
            String partnerName,
            LocalDate bizDate,
            String remark,
            Long reversalOf,
            String reversalOfNo,
            Instant postedAt,
            String createdBy,
            long rowVersion,
            BigDecimal totalQty,
            BigDecimal totalAmount,
            List<LineView> lines) {
    }

    public record LineView(
            Long id,
            Integer lineNo,
            Long itemId,
            String itemCode,
            String itemName,
            String uomCode,
            Long lotId,
            String lotNo,
            BigDecimal qty,
            BigDecimal unitCost,
            BigDecimal countedQty,
            BigDecimal amount,
            String remark) {
    }
}
