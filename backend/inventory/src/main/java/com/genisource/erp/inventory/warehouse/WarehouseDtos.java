package com.genisource.erp.inventory.warehouse;

import com.genisource.erp.platform.common.RecordStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payloads of the warehouse and location master.
 *
 * <p>Both live in one file because a bin belongs to a warehouse: they are maintained
 * together and split across two files they would only ever be changed apart.
 */
public final class WarehouseDtos {

    private WarehouseDtos() {
    }

    public record WarehouseRequest(
            @NotBlank(message = "{validation.code.required}")
            @Size(max = 32, message = "{validation.code.size}")
            @Pattern(regexp = "[^\\s]+", message = "{validation.code.whitespace}")
            String code,

            @NotBlank(message = "{validation.name.required}")
            @Size(max = 128, message = "{validation.name.size}")
            String name,

            @NotNull(message = "{validation.warehouseType.required}")
            WarehouseType warehouseType,

            @Size(max = 255)
            String address,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark,

            RecordStatus status) {
    }

    public record WarehouseView(
            Long id,
            String code,
            String name,
            WarehouseType warehouseType,
            String address,
            String remark,
            RecordStatus status,
            long rowVersion,
            long locationCount,
            long stockItemCount,
            List<LocationView> locations) {
    }

    public record LocationRequest(
            @NotNull(message = "{validation.warehouse.required}")
            Long warehouseId,

            @NotBlank(message = "{validation.code.required}")
            @Size(max = 32, message = "{validation.code.size}")
            @Pattern(regexp = "[^\\s]+", message = "{validation.code.whitespace}")
            String code,

            @NotBlank(message = "{validation.name.required}")
            @Size(max = 128, message = "{validation.name.size}")
            String name,

            @Size(max = 500, message = "{validation.remark.size}")
            String remark,

            RecordStatus status) {
    }

    public record LocationView(
            Long id,
            Long warehouseId,
            String warehouseName,
            String code,
            String name,
            String remark,
            RecordStatus status,
            long rowVersion) {
    }
}
