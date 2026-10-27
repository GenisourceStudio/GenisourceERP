package com.genisource.erp.masterdata.workcenter;

import com.genisource.erp.platform.common.RecordStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class WorkCenterDtos {

    private WorkCenterDtos() {
    }

    public record WorkCenterRequest(
            @NotBlank(message = "{validation.code.required}")
            @Size(max = 32, message = "{validation.code.size}")
            @Pattern(regexp = "[^\\s]+", message = "{validation.code.whitespace}")
            String code,

            @NotBlank(message = "{validation.name.required}")
            @Size(max = 128, message = "{validation.name.size}")
            String name,

            @Size(max = 64)
            String department,

            @Min(value = 1, message = "{validation.capacity.positive}")
            int capacityMinutesPerDay,

            @DecimalMin(value = "1", message = "{validation.efficiency.range}")
            @DecimalMax(value = "200", message = "{validation.efficiency.range}")
            BigDecimal efficiencyPercent,

            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal costRatePerHour,

            RecordStatus status) {
    }

    public record WorkCenterView(
            Long id,
            String code,
            String name,
            String department,
            int capacityMinutesPerDay,
            BigDecimal efficiencyPercent,
            int availableMinutesPerDay,
            BigDecimal costRatePerHour,
            RecordStatus status,
            long rowVersion,
            Instant updatedAt) {
    }

    public record WorkCenterOption(Long id, String code, String name) {
    }
}
