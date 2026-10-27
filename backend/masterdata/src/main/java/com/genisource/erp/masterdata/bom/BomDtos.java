package com.genisource.erp.masterdata.bom;

import com.genisource.erp.platform.common.RevisionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class BomDtos {

    private BomDtos() {
    }

    public record BomSubstituteRequest(
            @NotNull(message = "{validation.item.required}")
            Long itemId,

            @Min(value = 1, message = "{validation.priority.positive}")
            Integer priority,

            @Size(max = 255)
            String remark) {
    }

    public record BomLineRequest(
            /** Optional; the service assigns 10, 20, 30… when omitted. */
            Integer lineNo,

            @NotNull(message = "{validation.item.required}")
            Long componentItemId,

            @NotNull(message = "{validation.qty.required}")
            @DecimalMin(value = "0", inclusive = false, message = "{validation.qty.positive}")
            BigDecimal qty,

            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal lossRate,

            boolean phantom,

            @Size(max = 255)
            String remark,

            @Valid
            List<BomSubstituteRequest> substitutes) {
    }

    public record BomRequest(
            @NotNull(message = "{validation.item.required}")
            Long itemId,

            @NotBlank(message = "{validation.version.required}")
            @Size(max = 16, message = "{validation.version.size}")
            String version,

            @DecimalMin(value = "0", inclusive = false, message = "{validation.qty.positive}")
            BigDecimal baseQty,

            LocalDate effectiveFrom,
            LocalDate effectiveTo,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark,

            @Valid
            List<BomLineRequest> lines) {
    }

    /** Row of the BOM list; carries the parent item so the table needs no extra lookups. */
    public record BomSummary(
            Long id,
            Long itemId,
            String itemCode,
            String itemName,
            String version,
            RevisionStatus status,
            BigDecimal baseQty,
            int lineCount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Instant releasedAt,
            Instant updatedAt,
            String remark) {
    }

    public record BomSubstituteView(
            Long id,
            Long itemId,
            String itemCode,
            String itemName,
            int priority,
            String remark) {
    }

    public record BomLineView(
            Long id,
            int lineNo,
            Long componentItemId,
            String componentCode,
            String componentName,
            String componentSpec,
            String uomCode,
            BigDecimal qty,
            BigDecimal lossRate,
            boolean phantom,
            String remark,
            List<BomSubstituteView> substitutes) {
    }

    public record BomDetail(
            Long id,
            Long itemId,
            String itemCode,
            String itemName,
            String itemUomCode,
            String version,
            RevisionStatus status,
            BigDecimal baseQty,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Instant releasedAt,
            String remark,
            long rowVersion,
            List<BomLineView> lines) {
    }

    /**
     * One node of a multi-level explosion.
     *
     * <p>{@code qty} is the accumulated requirement for this node at this level, already
     * multiplied through every parent and inflated by loss rates. {@code perParentQty} is
     * what the parent line asks for, which is what the console shows next to a line.
     */
    public record BomTreeNode(
            Long itemId,
            String itemCode,
            String itemName,
            String itemSpec,
            String uomCode,
            BigDecimal qty,
            BigDecimal perParentQty,
            BigDecimal lossRate,
            boolean phantom,
            int level,
            String bomVersion,
            String source,
            List<BomTreeNode> children) {
    }
}
