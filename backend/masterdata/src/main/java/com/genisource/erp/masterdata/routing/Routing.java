package com.genisource.erp.masterdata.routing;

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Process route for an item: the ordered operations and where each one runs.
 *
 * <p>Follows the same revision lifecycle as {@link com.genisource.erp.masterdata.bom.Bom}
 * and for the same reason: a released route is what the shop floor works to, so it cannot
 * change under a running order.
 */
@Entity
@Table(name = "md_routing")
public class Routing extends VersionedTenantEntity {

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "version", nullable = false, length = 16)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RevisionStatus status = RevisionStatus.DRAFT;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "released_at")
    private Instant releasedAt;

    @OneToMany(mappedBy = "routing", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<RoutingOperation> operations = new ArrayList<>();

    protected Routing() {
    }

    public Routing(Long itemId, String version) {
        this.itemId = itemId;
        this.version = version;
    }

    public void ensureEditable() {
        if (!status.isEditable()) {
            throw ApiException.invalidState(
                    "Routing " + version + " is " + status + " and can no longer be edited. "
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
    }

    /** Total run minutes per unit across all operations, ignoring setup. */
    public java.math.BigDecimal runMinutesPerUnit() {
        return operations.stream()
                .map(RoutingOperation::getRunMinutesPerUnit)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public List<RoutingOperation> getOperations() {
        return operations;
    }

    public void replaceOperations(List<RoutingOperation> replacement) {
        operations.clear();
        for (RoutingOperation operation : replacement) {
            operation.attachTo(this);
            operations.add(operation);
        }
    }
}
