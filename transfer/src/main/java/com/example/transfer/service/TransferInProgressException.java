package com.example.transfer.service;

import java.util.UUID;

/**
 * Thrown when a transfer could not be finished because a downstream service was temporarily
 * unavailable (e.g. fraud or account unreachable, or a circuit is open). The transfer is left
 * PENDING — not terminally failed — so the client can retry with the same idempotency key to
 * resume it, or a reconciliation job can complete it later. Maps to HTTP 503.
 */
public class TransferInProgressException extends RuntimeException {

    private final UUID transferId;

    public TransferInProgressException(UUID transferId, String message) {
        super(message);
        this.transferId = transferId;
    }

    public UUID getTransferId() {
        return transferId;
    }
}
