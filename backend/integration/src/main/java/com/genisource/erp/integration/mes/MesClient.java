package com.genisource.erp.integration.mes;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * The calls this ERP makes to MES.
 *
 * <p>Three of them, matching the contract in docs/integration-mes.md: raise a work
 * order, report progress, and push an item's manufacturing attributes.
 *
 * <p>Every call carries an idempotency key. MES is told to answer the same key with
 * the same result, so a retry after a timeout cannot raise the same work order
 * twice. This is the difference between a network hiccup and a duplicate order on
 * the shop floor.
 */
@Component
public class MesClient {

    private final MesProperties properties;
    private final MesTokenProvider tokens;
    private final RestClient restClient;

    public MesClient(MesProperties properties, MesTokenProvider tokens, RestClient restClient) {
        this.properties = properties;
        this.tokens = tokens;
        this.restClient = restClient;
    }

    /** What MES says about a work order it accepted. */
    public record WorkOrderResponse(String mesWorkOrderNo, String status) {
    }

    /**
     * Raises a work order in MES.
     *
     * @param idempotencyKey one key per ERP order, however many times it is retried
     */
    public WorkOrderResponse raiseWorkOrder(String idempotencyKey, Map<String, Object> payload) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(properties.getBaseUrl() + "/api/integration/work-orders")
                    .header("Idempotency-Key", idempotencyKey)
                    .header("Authorization", "Bearer " + tokens.token())
                    .body(payload)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<
                            Map<String, Object>>() {
                    });
            if (response == null) {
                throw new MesException("MES accepted the work order but said nothing back");
            }
            Object number = response.get("mesWorkOrderNo");
            Object status = response.get("status");
            return new WorkOrderResponse(number == null ? null : number.toString(),
                    status == null ? "ACCEPTED" : status.toString());
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 409) {
                // MES already has this order. The contract says a 409 is a success: the
                // work order exists, which is all we were trying to achieve.
                return new WorkOrderResponse(null, "EXISTS");
            }
            if (status == 401 || status == 403) {
                tokens.invalidate();
                throw new MesException("MES rejected our token", ex);
            }
            throw new MesException("MES answered " + status + " for the work order", ex);
        }
    }

    /** Pushes an item's manufacturing attributes to MES. */
    public void pushItem(String itemCode, Map<String, Object> payload) {
        try {
            restClient.put()
                    .uri(properties.getBaseUrl() + "/api/integration/items/" + itemCode)
                    .header("Idempotency-Key", "erp-item-" + itemCode)
                    .header("Authorization", "Bearer " + tokens.token())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new MesException("MES answered " + ex.getStatusCode().value()
                    + " for item " + itemCode, ex);
        }
    }
}
