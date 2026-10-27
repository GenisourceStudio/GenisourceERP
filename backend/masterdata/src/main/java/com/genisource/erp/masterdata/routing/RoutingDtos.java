package com.genisource.erp.masterdata.routing;

import com.genisource.erp.masterdata.shared.RevisionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class RoutingDtos {

    private RoutingDtos() {
    }

    public record OperationRequest(
            /** Optional; the service assigns 10, 20, 30… when omitted. */
            Integer seq,

            @NotNull(message = "{validation.workCenter.required}")
            Long workCenterId,

            @NotBlank(message = "{validation.name.required}")
            @Size(max = 128, message = "{validation.name.size}")
            String name,

            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal setupMinutes,

            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal runMinutesPerUnit,

            @Size(max = 255)
            String remark) {
    }

    public record RoutingRequest(
            @NotNull(message = "{validation.item.required}")
            Long itemId,

            @NotBlank(message = "{validation.version.required}")
            @Size(max = 16, message = "{validation.version.size}")
            String version,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark,

            @Valid
            List<OperationRequest> operations) {
    }

    public record RoutingSummary(
            Long id,
            Long itemId,
            String itemCode,
            String itemName,
            String version,
            RevisionStatus status,
            int operationCount,
            BigDecimal runMinutesPerUnit,
            Instant releasedAt,
            Instant updatedAt,
            String remark) {
    }

    public record OperationView(
            Long id,
            int seq,
            Long workCenterId,
            String workCenterCode,
            String workCenterName,
            String name,
            BigDecimal setupMinutes,
            BigDecimal runMinutesPerUnit,
            String remark) {
    }

    public record RoutingDetail(
            Long id,
            Long itemId,
            String itemCode,
            String itemName,
            String itemUomCode,
            String version,
            RevisionStatus status,
            Instant releasedAt,
            String remark,
            long rowVersion,
            BigDecimal totalRunMinutesPerUnit,
            List<OperationView> operations) {
    }
}
