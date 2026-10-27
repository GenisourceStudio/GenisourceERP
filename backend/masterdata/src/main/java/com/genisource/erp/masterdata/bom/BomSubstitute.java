package com.genisource.erp.masterdata.bom;

import com.genisource.erp.platform.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * An approved alternative for a BOM line.
 *
 * <p>Purchase and production fall back to substitutes in {@code priority} order when the
 * primary component is short. Kept in its own table rather than a comma-separated column
 * so that a substitute can be reasoned about by MRP (M4) and by stock queries (M2).
 */
@Entity
@Table(name = "md_bom_substitute")
public class BomSubstitute extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false)
    private BomLine line;

    @Column(name = "substitute_item_id", nullable = false)
    private Long substituteItemId;

    /** Lower wins; equal priorities mean "any of them". */
    @Column(name = "priority", nullable = false)
    private int priority = 1;

    @Column(name = "remark", length = 255)
    private String remark;

    protected BomSubstitute() {
    }

    public BomSubstitute(Long substituteItemId, int priority) {
        this.substituteItemId = substituteItemId;
        this.priority = priority;
    }

    void attachTo(BomLine line) {
        this.line = line;
    }

    public BomLine getLine() {
        return line;
    }

    public Long getSubstituteItemId() {
        return substituteItemId;
    }

    public int getPriority() {
        return priority;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
