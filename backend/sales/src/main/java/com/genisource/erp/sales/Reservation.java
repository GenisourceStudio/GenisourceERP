package com.genisource.erp.sales;

import com.genisource.erp.platform.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Stock promised to one line of one sales order.
 *
 * <p>A reservation is a real thing, not a number on the balance. It says which balance
 * lines were promised to whom, which is what makes two things possible: cancelling one
 * order can give exactly those units back without disturbing another order's promise,
 * and a stock enquiry can say why a hundred on the shelf can only sell fifty.
 *
 * <p>One order line can be spread over several balance lines — fifty from the bin that
 * holds eighty rather than from the one that holds ten, which is what a picker would do.
 */
@Entity
@Table(name = "sal_reservation")
public class Reservation extends TenantScopedEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_line_id", nullable = false)
    private Long orderLineId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal qty;

    @Column(name = "qty_issued", nullable = false, precision = 18, scale = 6)
    private BigDecimal qtyIssued = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReservationStatus status = ReservationStatus.OPEN;

    protected Reservation() {
    }

    public Reservation(Long orderId,
                       Long orderLineId,
                       Long warehouseId,
                       Long locationId,
                       Long itemId,
                       Long lotId,
                       BigDecimal qty) {
        this.orderId = orderId;
        this.orderLineId = orderLineId;
        this.warehouseId = warehouseId;
        this.locationId = locationId;
        this.itemId = itemId;
        this.lotId = lotId;
        this.qty = qty;
    }

    /** How much of the promise is still waiting to be shipped. */
    public BigDecimal openQty() {
        BigDecimal open = qty.subtract(qtyIssued);
        return open.signum() > 0 ? open : BigDecimal.ZERO;
    }

    /** Records that a shipment took {@code qty} of what was promised. */
    public void issue(BigDecimal qty) {
        this.qtyIssued = this.qtyIssued.add(qty);
        if (this.qtyIssued.compareTo(this.qty) > 0) {
            this.qtyIssued = this.qty;
        }
        this.status = openQty().signum() == 0 ? ReservationStatus.FULFILLED : ReservationStatus.OPEN;
    }

    /** Gives the promise back, because the order was cancelled or closed. */
    public void release() {
        this.status = ReservationStatus.RELEASED;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getOrderLineId() {
        return orderLineId;
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

    public BigDecimal getQty() {
        return qty;
    }

    public BigDecimal getQtyIssued() {
        return qtyIssued;
    }

    public ReservationStatus getStatus() {
        return status;
    }
}
