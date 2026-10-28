package com.genisource.erp.integration.mes;

import com.genisource.erp.platform.audit.AuditService;
import com.genisource.erp.platform.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The outbox: messages to MES, and the worker that delivers them.
 *
 * <p>A failed message is retried with a growing gap — one minute, five, thirty, two
 * hours — because the most common cause of failure is MES being down, and hammering
 * a system that is down is how an integration turns a small outage into an outage for
 * both sides. After the last attempt the message is marked dead rather than retried
 * forever: a message nobody will ever deliver should stop asking, and a dead message
 * in the list is a thing a person can look at.
 */
@Service
public class MesOutboxService {

    private static final Logger log = LoggerFactory.getLogger(MesOutboxService.class);

    /** The gaps between attempts. The last one is the last attempt. */
    private static final List<Duration> BACKOFF = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
            Duration.ofHours(2));

    private final MesOutboxRepository outbox;
    private final MesClient client;
    private final MesProperties properties;
    private final AuditService audit;
    private final tools.jackson.databind.ObjectMapper mapper;
    private final com.genisource.erp.production.ProductionOrderRepository orders;

    public MesOutboxService(MesOutboxRepository outbox, MesClient client,
                            MesProperties properties, AuditService audit,
                            tools.jackson.databind.ObjectMapper mapper,
                            com.genisource.erp.production.ProductionOrderRepository orders) {
        this.outbox = outbox;
        this.client = client;
        this.properties = properties;
        this.audit = audit;
        this.mapper = mapper;
        this.orders = orders;
    }

    /**
     * Writes a message to be delivered.
     *
     * <p>Called inside the business transaction that caused it: a release that rolls
     * back leaves no message, and a message that exists means the release happened.
     */
    @Transactional
    public void enqueue(MesOutbox.MessageType type, String subject, String dedupeKey,
                        Map<String, Object> payload) {
        if (!properties.isConfigured()) {
            return;
        }
        if (outbox.findByDedupeKey(dedupeKey).isPresent()) {
            return;
        }
        outbox.save(new MesOutbox(type, subject, dedupeKey, mapper.writeValueAsString(payload)));
    }

    /**
     * Delivers the messages that are due.
     *
     * <p>Each message is delivered in its own transaction, so one that fails does not
     * hold up the ones behind it — and the failure is recorded rather than rolled
     * back into nothing.
     */
    @Scheduled(fixedDelayString = "${erp.mes.dispatch-interval-seconds:30}000")
    public void dispatchDue() {
        if (!properties.isConfigured()) {
            return;
        }
        List<MesOutbox> due = outbox.findByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                MesOutbox.Status.PENDING, Instant.now());
        for (MesOutbox message : due) {
            deliver(message.getId());
        }
    }

    /** Delivers one message. Public so a test (or an operator) can push it by hand. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long id) {
        MesOutbox message = outbox.findById(id).orElse(null);
        if (message == null || message.getStatus() != MesOutbox.Status.PENDING) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = mapper.readValue(message.getPayload(), Map.class);
            switch (message.getMessageType()) {
                case WORK_ORDER -> {
                    MesClient.WorkOrderResponse response =
                            client.raiseWorkOrder(message.getDedupeKey(), payload);
                    log.info("MES accepted work order {} as {}", message.getSubject(),
                            response.mesWorkOrderNo());
                    if (response.mesWorkOrderNo() != null && !response.mesWorkOrderNo().isBlank()) {
                        // Write the number MES knows the order by onto our order: a
                        // completion comes back with that number, and it is the only
                        // thing that links the two systems' views of one job.
                        orders.findByDocNo(message.getSubject()).ifPresent(order -> {
                            order.setMesWoNo(response.mesWorkOrderNo());
                            orders.save(order);
                        });
                    }
                }
                case ITEM -> client.pushItem(message.getSubject(), payload);
            }
            message.markSent();
            outbox.save(message);
            audit.record("integration.mes.deliver", CurrentUser.id().orElse(null),
                    CurrentUser.username().orElse(null), "int_mes_outbox", String.valueOf(id),
                    Map.of("subject", message.getSubject(),
                            "type", message.getMessageType().name()));
        } catch (RuntimeException ex) {
            String error = ex.getMessage() == null ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            int attempt = message.getAttempts() + 1;
            if (attempt >= properties.getMaxAttempts() || attempt >= BACKOFF.size()) {
                message.failed(error, Instant.now());
                message.markDead();
                log.error("Message {} to MES is dead after {} attempts: {}",
                        message.getSubject(), attempt, error);
            } else {
                message.failed(error, Instant.now().plus(BACKOFF.get(attempt - 1)));
                log.warn("Message {} to MES failed (attempt {}): {}",
                        message.getSubject(), attempt, error);
            }
            outbox.save(message);
        }
    }
}
