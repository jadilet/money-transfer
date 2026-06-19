package com.example.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One balanced accounting transaction — the postings that belong to it sum to zero. {@code transferId}
 * is unique: it's the idempotency key, so a redelivered Kafka event can't double-record.
 */
@Entity
@Table(name = "journal_entries",
        indexes = @Index(name = "ux_journal_entries_transfer_id", columnList = "transferId", unique = true))
public class JournalEntry {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID transferId;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected JournalEntry() {
        // for JPA
    }

    private JournalEntry(UUID transferId, String description) {
        this.id = UUID.randomUUID();
        this.transferId = transferId;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public static JournalEntry of(UUID transferId, String description) {
        return new JournalEntry(transferId, description);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
