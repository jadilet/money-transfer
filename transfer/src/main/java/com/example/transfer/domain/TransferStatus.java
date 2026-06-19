package com.example.transfer.domain;

/**
 * Lifecycle of a transfer. A transfer starts PENDING, then moves to exactly one
 * terminal state once the ledger posting succeeds or fails.
 */
public enum TransferStatus {
    PENDING,
    COMPLETED,
    /** The money-move was attempted but declined for a business reason (e.g. insufficient funds). */
    FAILED,
    /** We refused to attempt the move (e.g. fraud rejection). No money moved. */
    BLOCKED,
    /** Stuck PENDING past the max age and given up on by reconciliation. No money moved. */
    EXPIRED;

    /**
     * Terminal states are final decisions; PENDING is not, and may still be resumed. A transient
     * downstream outage must NOT produce a terminal state — it leaves the transfer PENDING so a
     * same-key retry (or reconciliation) can finish it.
     */
    public boolean isTerminal() {
        return this != PENDING;
    }
}
