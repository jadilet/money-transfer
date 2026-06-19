package com.example.transfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A money movement between two accounts. This is the transfer service's record of
 * intent and outcome; the authoritative balances live in the account service and
 * the immutable accounting entries live in the ledger.
 *
 * <p>The {@code idempotencyKey} is client-supplied and unique: a retried request
 * with the same key returns the existing transfer instead of moving money twice.
 */
@Entity
@Table(
        name = "transfers",
        indexes = @Index(name = "ux_transfers_idempotency_key", columnList = "idempotencyKey", unique = true)
)
public class Transfer {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private UUID fromAccountId;

    @Column(nullable = false, updatable = false)
    private UUID toAccountId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransferStatus status;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Transfer() {
        // for JPA
    }

    private Transfer(String idempotencyKey, UUID fromAccountId, UUID toAccountId,
                     BigDecimal amount, String currency) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.currency = currency;
        this.status = TransferStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Create a new transfer in PENDING state. */
    public static Transfer pending(String idempotencyKey, UUID fromAccountId, UUID toAccountId,
                                   BigDecimal amount, String currency) {
        return new Transfer(idempotencyKey, fromAccountId, toAccountId, amount, currency);
    }

    public void markCompleted() {
        this.status = TransferStatus.COMPLETED;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = TransferStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markBlocked(String reason) {
        this.status = TransferStatus.BLOCKED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markExpired(String reason) {
        this.status = TransferStatus.EXPIRED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    /** Bump the activity timestamp without changing state — used by the reconciler to claim a row. */
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
