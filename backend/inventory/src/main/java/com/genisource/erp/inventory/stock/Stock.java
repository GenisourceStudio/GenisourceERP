package com.genisource.erp.inventory.stock;

import com.genisource.erp.platform.common.VersionedTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * The live balance of one item in one place.
 *
 * <p>Four quantities, because "how much do we have" is never one number:
 * <ul>
 *   <li>{@code on_hand} — physically present;</li>
 *   <li>{@code reserved} — promised to a sales order (written by M3);</li>
 *   <li>{@code locked} — held by quality or production (written by M4);</li>
 *   <li>{@code inTransit} — issued from one warehouse, not yet received in the next.</li>
 * </ul>
 * Available is {@code onHand - reserved - locked}. The reserved and locked columns are
 * zero throughout M2: the columns exist because MRP and sales need them, and because a
 * balance table that has to gain two columns later is a balance table that has to be
 * migrated while it holds live data.
 *
 * <p>Value is tracked as a total plus a unit average. {@code totalAmount} is the
 * authoritative number — it accumulates exactly, without rounding drift — and
 * {@code avgCost} is derived from it. Incoming stock adds its cost to the total; the
 * average is then recomputed. Outgoing stock is valued at the current average.
 */
@Entity
@Table(name = "inv_stock")
public class Stock extends VersionedTenantEntity {

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "on_hand", nullable = false, precision = 18, scale = 6)
    private BigDecimal onHand = BigDecimal.ZERO;

    @Column(name = "reserved", nullable = false, precision = 18, scale = 6)
    private BigDecimal reserved = BigDecimal.ZERO;

    @Column(name = "in_transit", nullable = false, precision = 18, scale = 6)
    private BigDecimal inTransit = BigDecimal.ZERO;

    @Column(name = "locked", nullable = false, precision = 18, scale = 6)
    private BigDecimal locked = BigDecimal.ZERO;

    @Column(name = "avg_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal avgCost = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    protected Stock() {
    }

    public Stock(Long warehouseId, Long locationId, Long itemId, Long lotId) {
        this.warehouseId = warehouseId;
        this.locationId = locationId;
        this.itemId = itemId;
        this.lotId = lotId;
    }

    /** Quantity that may still be issued: on hand minus what is promised or held. */
    public BigDecimal available() {
        return onHand.subtract(reserved).subtract(locked);
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public Long getItemId() {
        return itemId;
    }

    public Long getLotId() {
        return lotId;
    }

    public BigDecimal getOnHand() {
        return onHand;
    }

    public void setOnHand(BigDecimal onHand) {
        this.onHand = onHand;
    }

    public BigDecimal getReserved() {
        return reserved;
    }

    public void setReserved(BigDecimal reserved) {
        this.reserved = reserved;
    }

    public BigDecimal getInTransit() {
        return inTransit;
    }

    public void setInTransit(BigDecimal inTransit) {
        this.inTransit = inTransit;
    }

    public BigDecimal getLocked() {
        return locked;
    }

    public void setLocked(BigDecimal locked) {
        this.locked = locked;
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }

    public void setAvgCost(BigDecimal avgCost) {
        this.avgCost = avgCost;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
