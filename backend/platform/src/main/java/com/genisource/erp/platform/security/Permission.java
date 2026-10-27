package com.genisource.erp.platform.security;

import com.genisource.erp.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A single thing a user may do, identified by a stable code such as
 * {@code masterdata:item:write}. The catalogue is global and shipped with the
 * application — permissions are never created by end users.
 */
@Entity
@Table(name = "sys_permission")
public class Permission extends BaseEntity {

    @Column(name = "code", nullable = false, length = 128, updatable = false)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "module", nullable = false, length = 64)
    private String module;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private PermissionType type;

    @Column(name = "description", length = 255)
    private String description;

    protected Permission() {
    }

    public Permission(String code, String name, String module, PermissionType type) {
        this.code = code;
        this.name = name;
        this.module = module;
        this.type = type;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getModule() {
        return module;
    }

    public PermissionType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }
}
