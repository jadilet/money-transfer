package com.example.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency record: one row per transfer the account service has processed, keyed by the
 * transfer id. A replayed apply finds this row and returns the recorded outcome instead of moving
 * money again.
 */
@Entity
@Table(name = "processed_transfers")
public class ProcessedTransfer {

    @Id
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransferResult result;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private Instant appliedAt;

    protected ProcessedTransfer() {
        // for JPA
    }

    private ProcessedTransfer(UUID transferId, TransferResult result, BigDecimal amount) {
        this.transferId = transferId;
        this.result = result;
        this.amount = amount;
        this.appliedAt = Instant.now();
    }

    public static ProcessedTransfer of(UUID transferId, TransferResult result, BigDecimal amount) {
        return new ProcessedTransfer(transferId, result, amount);
    }

    public UUID getTransferId() {
        return transferId;
    }

    public TransferResult getResult() {
        return result;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }
}
