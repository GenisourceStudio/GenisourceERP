package com.genisource.erp.masterdata.bom;

import com.genisource.erp.platform.common.RevisionStatus;
import com.genisource.erp.platform.common.ApiException;
import com.genisource.erp.platform.common.VersionedTenantEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bill of materials header: one engineering revision of how an item is built.
 *
 * <p>{@code baseQty} is the quantity the component list refers to — producing 100 sets
 * of a kit is naturally expressed as base 100 with component quantities per kit, which
 * keeps the line numbers readable instead of full of 0.01 factors.
 *
 * <p>Versions are labels, not numbers: factories use {@code A}, {@code B}, {@code V2},
 * {@code 2024-06}. The only rule is that they are unique per item and that at most one
 * revision of an item is {@code RELEASED} at a time — so "the current BOM" is never a
 * question with two answers.
 */
@Entity
@Table(name = "md_bom")
public class Bom extends VersionedTenantEntity {

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "version", nullable = false, length = 16)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RevisionStatus status = RevisionStatus.DRAFT;

    @Column(name = "base_qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal baseQty = BigDecimal.ONE;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "released_at")
    private Instant releasedAt;

    /**
     * Component lines. Owned by the header: editing a draft BOM replaces the whole list,
     * which is both simpler to reason about and exactly what the console sends.
     */
    @OneToMany(mappedBy = "bom", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<BomLine> lines = new ArrayList<>();

    protected Bom() {
    }

    public Bom(Long itemId, String version, BigDecimal baseQty) {
        this.itemId = itemId;
        this.version = version;
        this.baseQty = baseQty;
    }

    // ------------------------------------------------------------ behaviour

    /** Refuses any structural change once the revision has left the drafting room. */
    public void ensureEditable() {
        if (!status.isEditable()) {
            throw ApiException.invalidState(
                    "BOM " + version + " is " + status + " and can no longer be edited. "
                            + "Copy it to a new revision instead.");
        }
    }

    public boolean isReleased() {
        return status == RevisionStatus.RELEASED;
    }

    public void release() {
        this.status = RevisionStatus.RELEASED;
        this.releasedAt = Instant.now();
    }

    public void obsolete() {
        this.status = RevisionStatus.OBSOLETE;
    }

    public void tidy() {
        this.remark = remark == null || remark.isBlank() ? null : remark.trim();
        this.baseQty = baseQty == null ? BigDecimal.ONE : baseQty;
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw ApiException.badRequest("Effective-to date cannot precede effective-from date");
        }
    }

    public Long getItemId() {
        return itemId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public RevisionStatus getStatus() {
        return status;
    }

    public BigDecimal getBaseQty() {
        return baseQty;
    }

    public void setBaseQty(BigDecimal baseQty) {
        this.baseQty = baseQty;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public List<BomLine> getLines() {
        return lines;
    }

    /**
     * Replaces the component list, matching by line number.
     *
     * <p>Lines are updated in place instead of being dropped and re-added: Hibernate
     * writes inserts before deletes, so a delete-then-insert of line 10 would collide
     * with {@code uq_md_bom_line_no}. A re-inserted line would also lose its id, which
     * its substitutes point at.
     */
    public void replaceLines(List<BomLine> replacement) {
        Map<Integer, BomLine> incoming = new LinkedHashMap<>();
        for (BomLine line : replacement) {
            line.attachTo(this);
            incoming.put(line.getLineNo(), line);
        }
        for (BomLine existing : new ArrayList<>(lines)) {
            BomLine next = incoming.remove(existing.getLineNo());
            if (next == null) {
                lines.remove(existing);
            } else {
                existing.copyContentFrom(next);
            }
        }
        lines.addAll(incoming.values());
    }
}
