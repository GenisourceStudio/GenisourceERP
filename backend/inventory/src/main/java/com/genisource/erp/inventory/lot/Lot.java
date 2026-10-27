package com.genisource.erp.inventory.lot;

import com.genisource.erp.platform.common.RecordStatus;
import com.genisource.erp.platform.common.VersionedTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "inv_lot")
public class Lot extends VersionedTenantEntity {

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "lot_no", nullable = false, length = 64)
    private String lotNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "lot_type", nullable = false, length = 16)
    private LotType lotType = LotType.LOT;

    @Column(name = "production_date")
    private LocalDate productionDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "remark", length = 500)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;

    protected Lot() {
    }

    public Lot(Long itemId, String lotNo, LotType lotType) {
        this.itemId = itemId;
        this.lotNo = lotNo;
        this.lotType = lotType;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getLotNo() {
        return lotNo;
    }

    public void setLotNo(String lotNo) {
        this.lotNo = lotNo;
    }

    public LotType getLotType() {
        return lotType;
    }

    public void setLotType(LotType lotType) {
        this.lotType = lotType;
    }

    public LocalDate getProductionDate() {
        return productionDate;
    }

    public void setProductionDate(LocalDate productionDate) {
        this.productionDate = productionDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
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
}
