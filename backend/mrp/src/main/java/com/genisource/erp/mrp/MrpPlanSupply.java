package com.genisource.erp.mrp;

import com.genisource.erp.platform.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A recommendation: obtain this much of this item by this date.
 *
 * <p>Two quantities, and the difference between them is the point. {@code netQty} is what
 * the arithmetic says is missing; {@code qty} is what to actually obtain after rounding up
 * to the supplier's multiple. Keeping both lets a planner see that the shortage is four
 * and the order is ten, instead of wondering why the plan disagrees with the sums.
 */
@Entity
@Table(name = "mrp_plan_supply")
public class MrpPlanSupply extends TenantScopedEntity {

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "supply_type", nullable = false, length = 16)
    private MrpSupplyType supplyType;

    @Column(name = "qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal qty;

    @Column(name = "net_qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal netQty;

    @Column(name = "order_multiple", nullable = false, precision = 18, scale = 6)
    private BigDecimal orderMultiple = BigDecimal.ONE;

    /** The item's default supplier, for purchasing to start from. Null when nobody is set. */
    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MrpPlanStatus status = MrpPlanStatus.PLANNED;

    @Column(name = "converted_type", length = 32)
    private String convertedType;

    @Column(name = "converted_id", length = 64)
    private String convertedId;

    @Column(name = "converted_no", length = 32)
    private String convertedNo;

    @Column(name = "remark", length = 255)
    private String remark;

    protected MrpPlanSupply() {
    }

    public MrpPlanSupply(Long runId, Long itemId, MrpSupplyType supplyType) {
        this.runId = runId;
        this.itemId = itemId;
        this.supplyType = supplyType;
    }

    /** Fills in the recommendation; called again if more demand for the item turns up. */
    public void recommend(BigDecimal netQty, BigDecimal qty, BigDecimal orderMultiple,
                          Long supplierId, int leadTimeDays, LocalDate startDate,
                          LocalDate dueDate) {
        this.netQty = netQty;
        this.qty = qty;
        this.orderMultiple = orderMultiple;
        this.supplierId = supplierId;
        this.leadTimeDays = leadTimeDays;
        this.startDate = startDate;
        this.dueDate = dueDate;
    }

    /** Records the document this advice became. */
    public void convert(String convertedType, String convertedId, String convertedNo) {
        this.status = MrpPlanStatus.CONVERTED;
        this.convertedType = convertedType;
        this.convertedId = convertedId;
        this.convertedNo = convertedNo;
    }

    /** Drops the advice without acting on it. */
    public void ignore(String reason) {
        this.status = MrpPlanStatus.IGNORED;
        this.remark = reason;
    }

    public Long getRunId() {
        return runId;
    }

    public Long getItemId() {
        return itemId;
    }

    public MrpSupplyType getSupplyType() {
        return supplyType;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public BigDecimal getNetQty() {
        return netQty;
    }

    public BigDecimal getOrderMultiple() {
        return orderMultiple;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public int getLeadTimeDays() {
        return leadTimeDays;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public MrpPlanStatus getStatus() {
        return status;
    }

    public String getConvertedType() {
        return convertedType;
    }

    public String getConvertedId() {
        return convertedId;
    }

    public String getConvertedNo() {
        return convertedNo;
    }

    public String getRemark() {
        return remark;
    }
}
