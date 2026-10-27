package com.genisource.erp.inventory.lot;

import com.genisource.erp.platform.common.RecordStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class LotDtos {

    private LotDtos() {
    }

    public record LotRequest(
            @NotNull(message = "{validation.item.required}")
            Long itemId,

            @NotBlank(message = "{validation.lotNo.required}")
            @Size(max = 64, message = "{validation.lotNo.size}")
            String lotNo,

            @NotNull(message = "{validation.lotType.required}")
            LotType lotType,

            LocalDate productionDate,
            LocalDate expiryDate,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark,

            RecordStatus status) {
    }

    public record LotView(
            Long id,
            Long itemId,
            String itemCode,
            String itemName,
            String lotNo,
            LotType lotType,
            LocalDate productionDate,
            LocalDate expiryDate,
            String remark,
            RecordStatus status,
            BigDecimal onHand,
            BigDecimal totalAmount,
            long rowVersion) {
    }
}
