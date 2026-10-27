package com.genisource.erp.masterdata.partner;

import com.genisource.erp.platform.common.RecordStatus;
import com.genisource.erp.platform.common.VersionedTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Customer or supplier, or both.
 *
 * <p>Only one primary contact is stored in M1. Real factories have several (sales,
 * quality, finance) and that becomes {@code md_partner_contact} once purchase and sales
 * orders need to address a specific person — adding it now would be a table nobody fills.
 */
@Entity
@Table(name = "md_partner")
public class Partner extends VersionedTenantEntity {

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "short_name", length = 64)
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_type", nullable = false, length = 16)
    private PartnerType partnerType;

    @Column(name = "tax_no", length = 64)
    private String taxNo;

    @Column(name = "contact_name", length = 64)
    private String contactName;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "CNY";

    /** Days of credit granted; 0 means cash on delivery. */
    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays;

    /** Credit ceiling in the partner currency; 0 means unlimited is not modelled yet. */
    @Column(name = "credit_limit", nullable = false, precision = 18, scale = 4)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(name = "remark", length = 500)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;

    protected Partner() {
    }

    public Partner(String code, String name, PartnerType partnerType) {
        this.code = code;
        this.name = name;
        this.partnerType = partnerType;
    }

    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }

    public void tidy() {
        this.shortName = blankToNull(shortName);
        this.taxNo = blankToNull(taxNo);
        this.contactName = blankToNull(contactName);
        this.phone = blankToNull(phone);
        this.email = blankToNull(email);
        this.address = blankToNull(address);
        this.remark = blankToNull(remark);
        this.currency = currency == null || currency.isBlank() ? "CNY" : currency.trim().toUpperCase();
        this.creditLimit = creditLimit == null ? BigDecimal.ZERO : creditLimit;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public PartnerType getPartnerType() {
        return partnerType;
    }

    public void setPartnerType(PartnerType partnerType) {
        this.partnerType = partnerType;
    }

    public String getTaxNo() {
        return taxNo;
    }

    public void setTaxNo(String taxNo) {
        this.taxNo = taxNo;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getPaymentTermsDays() {
        return paymentTermsDays;
    }

    public void setPaymentTermsDays(int paymentTermsDays) {
        this.paymentTermsDays = paymentTermsDays;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public RecordStatus getStatus() {
        return status;
    }

    public void setStatus(RecordStatus status) {
        this.status = status;
    }
}
