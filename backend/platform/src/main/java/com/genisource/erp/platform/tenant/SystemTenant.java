package com.genisource.erp.platform.tenant;

import com.genisource.erp.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A tenant is a factory group. The table itself is <em>not</em> tenant scoped — it is
 * the registry that every other table points at.
 */
@Entity
@Table(name = "sys_tenant")
public class SystemTenant extends BaseEntity {

    @Column(name = "code", nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TenantStatus status = TenantStatus.ACTIVE;

    protected SystemTenant() {
    }

    public SystemTenant(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }
}
