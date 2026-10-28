package com.genisource.erp.integration.mes;

import com.genisource.erp.platform.common.TenantAppendOnlyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A completion MES has reported.
 *
 * <p>The row exists for one reason: the unique {@code mes_completion_id}. MES
 * retries, networks retry, and a factory that books the same batch of finished goods
 * twice has a stock figure nobody can believe. The row is written in the same
 * transaction as the stock it books, so either both happened or neither did.
 */
@Entity
@Table(name = "int_mes_completion")
public class MesCompletion extends TenantAppendOnlyEntity {

    @Column(name = "mes_completion_id", nullable = false, length = 64)
    private String mesCompletionId;

    @Column(name = "mes_work_order_no", nullable = false, length = 64)
    private String mesWorkOrderNo;

    @Column(name = "production_order_id")
    private Long productionOrderId;

    @Column(name = "completed_qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal completedQty = BigDecimal.ZERO;

    @Column(name = "scrap_qty", nullable = false, precision = 18, scale = 6)
    private BigDecimal scrapQty = BigDecimal.ZERO;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt = Instant.now();

    protected MesCompletion() {
    }

    public MesCompletion(String mesCompletionId, String mesWorkOrderNo, Long productionOrderId,
                         BigDecimal completedQty, BigDecimal scrapQty) {
        this.mesCompletionId = mesCompletionId;
        this.mesWorkOrderNo = mesWorkOrderNo;
        this.productionOrderId = productionOrderId;
        this.completedQty = completedQty;
        this.scrapQty = scrapQty;
    }

    public String getMesCompletionId() {
        return mesCompletionId;
    }

    public String getMesWorkOrderNo() {
        return mesWorkOrderNo;
    }

    public Long getProductionOrderId() {
        return productionOrderId;
    }

    public BigDecimal getCompletedQty() {
        return completedQty;
    }

    public BigDecimal getScrapQty() {
        return scrapQty;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
