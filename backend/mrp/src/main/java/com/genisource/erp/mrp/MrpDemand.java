package com.genisource.erp.mrp;

import com.genisource.erp.platform.common.TenantAppendOnlyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Why one item was on the list: every number that went into the net requirement.
 *
 * <p>Written for every item the run looked at, including the ones that turned out to be
 * covered. A planner who asks "we have stock, why did it not suggest anything" is
 * answered by a row saying gross four, on hand four, net zero — and a table that only
 * kept the shortages could not answer at all.
 *
 * <p>The netting is spelled out rather than left implicit:
 * <pre>
 *   net = gross + safety + reserved − on hand − locked − in transit
 * </pre>
 * {@code reserved} is stock promised to orders that fall <em>outside</em> the planning
 * window. Promises inside the window appear twice by nature — the units are in the stock
 * figure and the order is in the gross figure — so they cancel and are not recorded here.
 * Counting them would net the same requirement twice and the company would buy material
 * it already owns; ignoring the ones outside the window would let the plan promise the
 * same units a second time.
 */
@Entity
@Table(name = "mrp_demand")
public class MrpDemand extends TenantAppendOnlyEntity {

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** Depth in the bill of materials: 0 is a finished good demanded directly by a customer. */
    @Column(name = "item_level", nullable = false)
    private int itemLevel;

    @Column(name = "gross_qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal grossQty = BigDecimal.ZERO;

    @Column(name = "on_hand", nullable = false, precision = 18, scale = 6)
    private BigDecimal onHand = BigDecimal.ZERO;

    @Column(name = "reserved_qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal reservedQty = BigDecimal.ZERO;

    @Column(name = "locked_qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal lockedQty = BigDecimal.ZERO;

    @Column(name = "in_transit", nullable = false, precision = 18, scale = 6)
    private BigDecimal inTransit = BigDecimal.ZERO;

    @Column(name = "safety_stock", nullable = false, precision = 18, scale = 6)
    private BigDecimal safetyStock = BigDecimal.ZERO;

    @Column(name = "net_qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal netQty = BigDecimal.ZERO;

    @Column(name = "required_date")
    private LocalDate requiredDate;

    @Column(name = "from_sales", nullable = false, precision = 18, scale = 6)
    private BigDecimal fromSales = BigDecimal.ZERO;

    @Column(name = "from_components", nullable = false, precision = 18, scale = 6)
    private BigDecimal fromComponents = BigDecimal.ZERO;

    @Column(name = "shortage", nullable = false)
    private boolean shortage;

    protected MrpDemand() {
    }

    public MrpDemand(Long runId, Long itemId, int itemLevel) {
        this.runId = runId;
        this.itemId = itemId;
        this.itemLevel = itemLevel;
    }

    /** Writes the whole calculation at once, so the columns can never tell different stories. */
    public void snapshot(BigDecimal grossQty, BigDecimal onHand, BigDecimal reservedQty,
                         BigDecimal lockedQty, BigDecimal inTransit, BigDecimal safetyStock,
                         BigDecimal netQty, LocalDate requiredDate, BigDecimal fromSales,
                         BigDecimal fromComponents) {
        this.grossQty = grossQty;
        this.onHand = onHand;
        this.reservedQty = reservedQty;
        this.lockedQty = lockedQty;
        this.inTransit = inTransit;
        this.safetyStock = safetyStock;
        this.netQty = netQty;
        this.requiredDate = requiredDate;
        this.fromSales = fromSales;
        this.fromComponents = fromComponents;
        this.shortage = netQty.signum() > 0;
    }

    public Long getRunId() {
        return runId;
    }

    public Long getItemId() {
        return itemId;
    }

    public int getItemLevel() {
        return itemLevel;
    }

    public BigDecimal getGrossQty() {
        return grossQty;
    }

    public BigDecimal getOnHand() {
        return onHand;
    }

    public BigDecimal getReservedQty() {
        return reservedQty;
    }

    public BigDecimal getLockedQty() {
        return lockedQty;
    }

    public BigDecimal getInTransit() {
        return inTransit;
    }

    public BigDecimal getSafetyStock() {
        return safetyStock;
    }

    public BigDecimal getNetQty() {
        return netQty;
    }

    public LocalDate getRequiredDate() {
        return requiredDate;
    }

    public BigDecimal getFromSales() {
        return fromSales;
    }

    public BigDecimal getFromComponents() {
        return fromComponents;
    }

    public boolean isShortage() {
        return shortage;
    }
}
