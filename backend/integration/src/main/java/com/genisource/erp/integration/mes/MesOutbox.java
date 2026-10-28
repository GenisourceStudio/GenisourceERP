package com.genisource.erp.integration.mes;

import com.genisource.erp.platform.common.VersionedTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A message waiting to be delivered to MES.
 *
 * <p>The outbox is the honest answer to "how do you release an order and tell MES
 * about it in one go?" You cannot: they are two systems. So the release writes the
 * message in its own transaction and a worker delivers it afterwards. If MES is down,
 * the message waits and is retried; if the release rolls back, there is no message.
 *
 * <p>{@code dedupeKey} is what keeps a retry from becoming a second work order: the
 * same key always describes the same message, and MES is told to answer it the same
 * way twice.
 */
@Entity
@Table(name = "int_mes_outbox")
public class MesOutbox extends VersionedTenantEntity {

    public enum MessageType {
        WORK_ORDER,
        ITEM
    }

    public enum Status {
        PENDING,
        SENT,
        DEAD
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 32)
    private MessageType messageType;

    @Column(name = "subject", nullable = false, length = 64)
    private String subject;

    @Column(name = "dedupe_key", nullable = false, length = 128)
    private String dedupeKey;

    /**
     * The message body, as JSON text.
     *
     * <p>Plain text on purpose: the payload is an opaque envelope that only this
     * module reads, and storing it as text keeps the reading and the writing in one
     * place — the mapper — instead of in the ORM's type handling.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected MesOutbox() {
    }

    public MesOutbox(MessageType messageType, String subject, String dedupeKey,
                     String payload) {
        this.messageType = messageType;
        this.subject = subject;
        this.dedupeKey = dedupeKey;
        this.payload = payload;
    }

    /** Records a failed attempt and pushes the next one further out. */
    public void failed(String error, Instant nextAttemptAt) {
        this.attempts += 1;
        this.lastError = error;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markSent() {
        this.status = Status.SENT;
        this.sentAt = Instant.now();
    }

    public void markDead() {
        this.status = Status.DEAD;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public String getSubject() {
        return subject;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public String getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
