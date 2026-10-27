package com.genisource.erp.platform.common;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.time.Instant;

/**
 * Base class for rows that are written once and never changed.
 *
 * <p>The inventory ledger is the example: an entry is appended when a document is
 * posted and is never touched again, not even by a reversal — the correction is a new
 * row beside it. Such a row has no {@code updated_at}, because "when was this last
 * changed" would always answer the same thing, and a column that cannot vary is a
 * column that will eventually be read as if it could.
 */
@MappedSuperclass
public abstract class AppendOnlyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
