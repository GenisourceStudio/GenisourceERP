package com.genisource.erp.inventory.warehouse;

import com.genisource.erp.platform.common.RecordStatus;
import com.genisource.erp.platform.common.VersionedTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A bin inside a warehouse.
 *
 * <p>Locations are optional. A small factory can keep stock at warehouse level and
 * never think about bins; a larger one needs them for picking. Because the stock key
 * treats a missing location as "the whole warehouse", stock can start at warehouse
 * level and be refined later without a migration.
 */
@Entity
@Table(name = "inv_location")
public class Location extends VersionedTenantEntity {

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "remark", length = 500)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;

    protected Location() {
    }

    public Location(Long warehouseId, String code, String name) {
        this.warehouseId = warehouseId;
        this.code = code;
        this.name = name;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public void setStatus(RecordStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return status.isActive();
    }
}
