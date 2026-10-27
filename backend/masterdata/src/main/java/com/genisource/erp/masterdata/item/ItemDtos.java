package com.genisource.erp.masterdata.item;

import com.genisource.erp.masterdata.shared.RecordStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request and response payloads of the item master.
 *
 * <p>One request record doubles as create and update payload: the console sends the whole
 * form either way, and partial updates would make "did the user clear this field or was
 * it simply not sent" undecidable.
 */
public final class ItemDtos {

    private ItemDtos() {
    }

    public record ItemRequest(
            @NotBlank(message = "{validation.code.required}")
            @Size(max = 64, message = "{validation.code.size}")
            @Pattern(regexp = "[^\\s]+", message = "{validation.code.whitespace}")
            String code,

            @NotBlank(message = "{validation.name.required}")
            @Size(max = 200, message = "{validation.name.size}")
            String name,

            @Size(max = 255, message = "{validation.spec.size}")
            String spec,

            Long categoryId,

            @NotNull(message = "{validation.type.required}")
            ItemType itemType,

            @NotBlank(message = "{validation.uom.required}")
            @Size(max = 16)
            String uomCode,

            @Size(max = 16)
            String purchaseUomCode,

            @DecimalMin(value = "0", inclusive = false, message = "{validation.factor.positive}")
            BigDecimal purchaseFactor,

            boolean purchased,
            @Min(value = 0, message = "{validation.nonNegative}")
            int purchaseLeadTimeDays,
            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal minOrderQty,
            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal safetyStock,
            Long defaultSupplierId,

            boolean sold,
            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal salePrice,

            boolean manufactured,
            boolean lotControlled,
            boolean serialControlled,
            @Min(value = 0, message = "{validation.nonNegative}")
            Integer shelfLifeDays,

            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal standardCost,
            @Size(max = 8)
            String currency,
            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal taxRate,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark,

            RecordStatus status) {
    }

    public record ItemView(
            Long id,
            String code,
            String name,
            String spec,
            Long categoryId,
            String categoryName,
            ItemType itemType,
            String uomCode,
            String uomName,
            String purchaseUomCode,
            BigDecimal purchaseFactor,
            boolean purchased,
            int purchaseLeadTimeDays,
            BigDecimal minOrderQty,
            BigDecimal safetyStock,
            Long defaultSupplierId,
            String defaultSupplierName,
            boolean sold,
            BigDecimal salePrice,
            boolean manufactured,
            boolean lotControlled,
            boolean serialControlled,
            Integer shelfLifeDays,
            BigDecimal standardCost,
            String currency,
            BigDecimal taxRate,
            String remark,
            RecordStatus status,
            long rowVersion,
            Instant updatedAt) {
    }

    /** Minimal shape for pickers; keeps combobox payloads small. */
    public record ItemOption(Long id, String code, String name, String spec, String uomCode) {
    }

    public record CategoryRequest(
            @NotBlank(message = "{validation.code.required}")
            @Size(max = 32, message = "{validation.code.size}")
            String code,

            @NotBlank(message = "{validation.name.required}")
            @Size(max = 128, message = "{validation.name.size}")
            String name,

            Long parentId,
            Integer sortOrder,
            RecordStatus status) {
    }

    public record CategoryView(
            Long id,
            String code,
            String name,
            Long parentId,
            String parentName,
            int sortOrder,
            RecordStatus status,
            long itemCount) {
    }

    public record UomView(String code, String name, String nameEn, short decimals, String label) {
    }
}
