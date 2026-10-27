package com.genisource.erp.masterdata.partner;

import com.genisource.erp.platform.common.RecordStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class PartnerDtos {

    private PartnerDtos() {
    }

    public record PartnerRequest(
            @NotBlank(message = "{validation.code.required}")
            @Size(max = 32, message = "{validation.code.size}")
            @Pattern(regexp = "[^\\s]+", message = "{validation.code.whitespace}")
            String code,

            @NotBlank(message = "{validation.name.required}")
            @Size(max = 200, message = "{validation.name.size}")
            String name,

            @Size(max = 64, message = "{validation.shortName.size}")
            String shortName,

            @NotNull(message = "{validation.type.required}")
            PartnerType partnerType,

            @Size(max = 64)
            String taxNo,

            @Size(max = 64)
            String contactName,

            @Size(max = 32)
            String phone,

            @Email(message = "{validation.email.invalid}")
            @Size(max = 128)
            String email,

            @Size(max = 255)
            String address,

            @Size(max = 8)
            String currency,

            @Min(value = 0, message = "{validation.nonNegative}")
            int paymentTermsDays,

            @DecimalMin(value = "0", message = "{validation.nonNegative}")
            BigDecimal creditLimit,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark,

            RecordStatus status) {
    }

    public record PartnerView(
            Long id,
            String code,
            String name,
            String shortName,
            PartnerType partnerType,
            String taxNo,
            String contactName,
            String phone,
            String email,
            String address,
            String currency,
            int paymentTermsDays,
            BigDecimal creditLimit,
            String remark,
            RecordStatus status,
            long rowVersion,
            Instant updatedAt) {
    }

    public record PartnerOption(Long id, String code, String name, PartnerType partnerType) {
    }
}
