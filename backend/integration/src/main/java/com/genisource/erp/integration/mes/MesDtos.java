package com.genisource.erp.integration.mes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The shapes MES sends us.
 *
 * <p>These are the contract from docs/integration-mes.md, in the words of a program
 * rather than of a document. Everything MES sends has an identifier we can refuse to
 * book twice, and a quantity we can check against what was planned.
 */
public final class MesDtos {

    private MesDtos() {
    }

    /** One material MES consumed, with the lot it came from. */
    public record MaterialUsage(
            @NotBlank(message = "{validation.item.required}")
            String itemCode,

            @NotNull(message = "{validation.qty.required}")
            BigDecimal qty,

            String lotNo) {
    }

    /**
     * MES says the order is finished.
     *
     * @param mesCompletionId the id MES retries with; the ERP books it once
     * @param mesWorkOrderNo  which order
     * @param completedQty    how many good ones
     * @param scrapQty        how many were scrapped
     */
    public record CompletionRequest(
            @NotBlank(message = "{validation.mesCompletionId.required}")
            String mesCompletionId,

            @NotBlank(message = "{validation.mesWorkOrderNo.required}")
            String mesWorkOrderNo,

            @NotNull(message = "{validation.qty.required}")
            BigDecimal completedQty,

            BigDecimal scrapQty,

            Instant completedAt,

            String warehouseCode,

            String lotNo,

            List<MaterialUsage> materials) {
    }

    public record CompletionResponse(
            String mesCompletionId,
            /** True when this completion had already been booked and nothing was done. */
            boolean duplicate,
            Long productionOrderId,
            String message) {
    }

    /** MES says how far the order has got. */
    public record ProgressRequest(
            @NotBlank(message = "{validation.mesWorkOrderNo.required}")
            String mesWorkOrderNo,

            @NotNull(message = "{validation.qty.required}")
            BigDecimal completedQty,

            Instant reportedAt) {
    }

    public record ProgressResponse(String mesWorkOrderNo, BigDecimal completedQty) {
    }

    /** One message in the outbox, for the integration screen. */
    public record OutboxView(
            Long id,
            String messageType,
            String subject,
            String status,
            int attempts,
            String lastError,
            java.time.Instant nextAttemptAt,
            java.time.Instant sentAt) {
    }
}
