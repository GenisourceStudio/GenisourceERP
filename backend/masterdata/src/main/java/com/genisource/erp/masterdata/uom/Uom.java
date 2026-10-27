package com.genisource.erp.masterdata.uom;

import com.genisource.erp.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Unit of measure. A global dictionary, like permissions: {@code KG} means the same
 * thing in every factory.
 *
 * <p>Not tenant scoped on purpose. M1 exposes units read-only; custom units arrive with
 * M2 where per-item conversion factors become meaningful.
 */
@Entity
@Table(name = "md_uom")
public class Uom extends BaseEntity {

    @Column(name = "code", nullable = false, length = 16)
    private String code;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "name_en", nullable = false, length = 64)
    private String nameEn;

    @Column(name = "decimals", nullable = false)
    private short decimals;

    protected Uom() {
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public short getDecimals() {
        return decimals;
    }
}
