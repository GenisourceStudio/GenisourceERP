package com.genisource.erp.platform.audit;

import com.genisource.erp.platform.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only record of a state-changing action.
 *
 * <p>{@code detail} keeps the before/after snapshot as JSONB: cheap to write, queryable
 * when an auditor asks "who changed this price and from what". Rows are never updated
 * or deleted by the application.
 */
@Entity
@Table(name = "sys_audit_log")
public class AuditLog extends TenantScopedEntity {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "resource_type", length = 64)
    private String resourceType;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail")
    private String detail;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    protected AuditLog() {
    }

    public AuditLog(Long userId, String username, String action) {
        this.userId = userId;
        this.username = username;
        this.action = action;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getDetail() {
        return detail;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void describe(String resourceType, String resourceId) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public void setOrigin(String ipAddress, String userAgent) {
        this.ipAddress = truncate(ipAddress, 64);
        this.userAgent = truncate(userAgent, 255);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
