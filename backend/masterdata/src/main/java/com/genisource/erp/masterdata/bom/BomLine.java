package com.genisource.erp.masterdata.bom;

import com.genisource.erp.platform.common.TenantScopedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One component of a BOM: how much of which item goes into {@code baseQty} units of the
 * parent.
 *
 * <p>Lines carry no optimistic-lock column of their own. They are only ever written as
 * part of replacing the whole list of a draft BOM, and the header's version covers that
 * transaction — a per-line version would protect nothing and cost a query.
 */
@Entity
@Table(name = "md_bom_line")
public class BomLine extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bom_id", nullable = false)
    private Bom bom;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "component_item_id", nullable = false)
    private Long componentItemId;

    @Column(name = "qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal qty;

    /** Expected process loss in percent; 2 means 2 % more material is issued. */
    @Column(name = "loss_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal lossRate = BigDecimal.ZERO;

    /** Phantom lines are exploded through: no work order, no stock of their own. */
    @Column(name = "phantom", nullable = false)
    private boolean phantom;

    @Column(name = "remark", length = 255)
    private String remark;

    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("priority ASC")
    @org.hibernate.annotations.BatchSize(size = 100)
    private List<BomSubstitute> substitutes = new ArrayList<>();

    protected BomLine() {
    }

    public BomLine(int lineNo, Long componentItemId, BigDecimal qty) {
        this.lineNo = lineNo;
        this.componentItemId = componentItemId;
        this.qty = qty;
    }

    void attachTo(Bom bom) {
        this.bom = bom;
    }

    public void replaceSubstitutes(List<BomSubstitute> replacement) {
        substitutes.clear();
        for (BomSubstitute substitute : replacement) {
            substitute.attachTo(this);
            substitutes.add(substitute);
        }
    }

    public void tidy() {
        this.remark = remark == null || remark.isBlank() ? null : remark.trim();
        this.lossRate = lossRate == null ? BigDecimal.ZERO : lossRate;
    }

    public Bom getBom() {
        return bom;
    }

    public int getLineNo() {
        return lineNo;
    }

    public void setLineNo(int lineNo) {
        this.lineNo = lineNo;
    }

    public Long getComponentItemId() {
        return componentItemId;
    }

    public void setComponentItemId(Long componentItemId) {
        this.componentItemId = componentItemId;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getLossRate() {
        return lossRate;
    }

    public void setLossRate(BigDecimal lossRate) {
        this.lossRate = lossRate;
    }

    public boolean isPhantom() {
        return phantom;
    }

    public void setPhantom(boolean phantom) {
        this.phantom = phantom;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<BomSubstitute> getSubstitutes() {
        return substitutes;
    }
}
