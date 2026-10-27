package com.genisource.erp.masterdata.item;

import com.genisource.erp.platform.common.RecordStatus;
import com.genisource.erp.platform.common.VersionedTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * The item master.
 *
 * <p>One table carries every facet — purchasing, sales, manufacturing, costing — because
 * they describe the same physical thing and splitting them into side tables only creates
 * half-populated rows. Facets are switched on by boolean flags ({@code purchased},
 * {@code sold}, {@code manufactured}) so a raw material and a finished good are stored
 * identically and the console just renders the relevant blocks.
 *
 * <p>Quantities are {@link BigDecimal} with six decimals: enough for grams and metres
 * without falling back to floating point, which is never acceptable for stock.
 */
@Entity
@Table(name = "md_item")
public class Item extends VersionedTenantEntity {

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "spec", length = 255)
    private String spec;

    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 16)
    private ItemType itemType;

    // ---------------------------------------------------------------- units

    @Column(name = "uom_code", nullable = false, length = 16)
    private String uomCode;

    @Column(name = "purchase_uom_code", length = 16)
    private String purchaseUomCode;

    /** How many stock units one purchasing unit contains (1 box = 100 pcs → 100). */
    @Column(name = "purchase_factor", nullable = false, precision = 18, scale = 6)
    private BigDecimal purchaseFactor = BigDecimal.ONE;

    // ----------------------------------------------------------- purchasing

    @Column(name = "purchased", nullable = false)
    private boolean purchased;

    @Column(name = "purchase_lead_time_days", nullable = false)
    private int purchaseLeadTimeDays;

    @Column(name = "min_order_qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal minOrderQty = BigDecimal.ZERO;

    @Column(name = "safety_stock", nullable = false, precision = 18, scale = 6)
    private BigDecimal safetyStock = BigDecimal.ZERO;

    @Column(name = "default_supplier_id")
    private Long defaultSupplierId;

    // ---------------------------------------------------------------- sales

    @Column(name = "sold", nullable = false)
    private boolean sold;

    @Column(name = "sale_price", precision = 18, scale = 4)
    private BigDecimal salePrice;

    // ------------------------------------------------------- manufacturing

    @Column(name = "manufactured", nullable = false)
    private boolean manufactured;

    @Column(name = "lot_controlled", nullable = false)
    private boolean lotControlled;

    @Column(name = "serial_controlled", nullable = false)
    private boolean serialControlled;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    // ------------------------------------------------------------- costing

    /**
     * Standard cost per stock unit. Authoritative until M2 introduces moving average
     * costing from actual receipts; afterwards it stays as the planning cost MRP uses.
     */
    @Column(name = "standard_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal standardCost = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "CNY";

    @Column(name = "tax_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxRate = new BigDecimal("13");

    @Column(name = "remark", length = 500)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RecordStatus status = RecordStatus.ACTIVE;

    protected Item() {
    }

    public Item(String code, String name, ItemType itemType, String uomCode) {
        this.code = code;
        this.name = name;
        this.itemType = itemType;
        this.uomCode = uomCode;
    }

    // ------------------------------------------------------------ behaviour

    public boolean isActive() {
        return status == RecordStatus.ACTIVE;
    }

    /** Normalises optional blanks so the database stores {@code null} rather than "". */
    public void tidy() {
        this.spec = blankToNull(spec);
        this.remark = blankToNull(remark);
        this.currency = currency == null || currency.isBlank() ? "CNY" : currency.trim().toUpperCase();
        this.purchaseUomCode = purchaseUomCode == null || purchaseUomCode.isBlank()
                ? uomCode
                : purchaseUomCode.trim();
        this.purchaseFactor = purchaseFactor == null ? BigDecimal.ONE : purchaseFactor;
        this.minOrderQty = orZero(minOrderQty);
        this.safetyStock = orZero(safetyStock);
        this.standardCost = orZero(standardCost);
        this.taxRate = orZero(taxRate);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // -------------------------------------------------------------- getters

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

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    public String getUomCode() {
        return uomCode;
    }

    public void setUomCode(String uomCode) {
        this.uomCode = uomCode;
    }

    public String getPurchaseUomCode() {
        return purchaseUomCode;
    }

    public void setPurchaseUomCode(String purchaseUomCode) {
        this.purchaseUomCode = purchaseUomCode;
    }

    public BigDecimal getPurchaseFactor() {
        return purchaseFactor;
    }

    public void setPurchaseFactor(BigDecimal purchaseFactor) {
        this.purchaseFactor = purchaseFactor;
    }

    public boolean isPurchased() {
        return purchased;
    }

    public void setPurchased(boolean purchased) {
        this.purchased = purchased;
    }

    public int getPurchaseLeadTimeDays() {
        return purchaseLeadTimeDays;
    }

    public void setPurchaseLeadTimeDays(int purchaseLeadTimeDays) {
        this.purchaseLeadTimeDays = purchaseLeadTimeDays;
    }

    public BigDecimal getMinOrderQty() {
        return minOrderQty;
    }

    public void setMinOrderQty(BigDecimal minOrderQty) {
        this.minOrderQty = minOrderQty;
    }

    public BigDecimal getSafetyStock() {
        return safetyStock;
    }

    public void setSafetyStock(BigDecimal safetyStock) {
        this.safetyStock = safetyStock;
    }

    public Long getDefaultSupplierId() {
        return defaultSupplierId;
    }

    public void setDefaultSupplierId(Long defaultSupplierId) {
        this.defaultSupplierId = defaultSupplierId;
    }

    public boolean isSold() {
        return sold;
    }

    public void setSold(boolean sold) {
        this.sold = sold;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public boolean isManufactured() {
        return manufactured;
    }

    public void setManufactured(boolean manufactured) {
        this.manufactured = manufactured;
    }

    public boolean isLotControlled() {
        return lotControlled;
    }

    public void setLotControlled(boolean lotControlled) {
        this.lotControlled = lotControlled;
    }

    public boolean isSerialControlled() {
        return serialControlled;
    }

    public void setSerialControlled(boolean serialControlled) {
        this.serialControlled = serialControlled;
    }

    public Integer getShelfLifeDays() {
        return shelfLifeDays;
    }

    public void setShelfLifeDays(Integer shelfLifeDays) {
        this.shelfLifeDays = shelfLifeDays;
    }

    public BigDecimal getStandardCost() {
        return standardCost;
    }

    public void setStandardCost(BigDecimal standardCost) {
        this.standardCost = standardCost;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
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
