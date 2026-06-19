package com.example.transfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox row. Written in the same database transaction as the transfer's terminal
 * state change, so "the transfer completed" and "an event to publish" commit atomically — the event
 * can never be lost. A relay later reads PENDING rows and publishes them to Kafka.
 */
@Entity
@Table(
        name = "outbox_events",
        indexes = @Index(name = "ix_outbox_status_created", columnList = "status, createdAt")
)
public class OutboxEvent {

    @Id
    private UUID id;

    /** Id of the aggregate this event is about (the transfer id). */
    @Column(nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(nullable = false, updatable = false, length = 64)
    private String type;

    @Column(nullable = false, updatable = false, length = 4000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant publishedAt;

    protected OutboxEvent() {
        // for JPA
    }

    private OutboxEvent(String type, UUID aggregateId, String payload) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public static OutboxEvent of(String type, UUID aggregateId, String payload) {
        return new OutboxEvent(type, aggregateId, payload);
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
