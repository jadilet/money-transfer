package com.example.transfer.service;

import com.example.transfer.domain.Transfer;
import java.util.UUID;

/**
 * Orchestrates transfers: idempotent creation and lookup. The implementation
 * (step 4) dedupes on the idempotency key, persists a PENDING transfer, calls the
 * ledger, and records the terminal state.
 */
public interface TransferService {

    Transfer create(CreateTransferCommand command);

    Transfer getById(UUID id);

    /**
     * Re-run the saga for a non-terminal transfer (used by retry and reconciliation). A terminal
     * transfer is returned unchanged. May throw {@link TransferInProgressException} if a downstream
     * is still unavailable.
     */
    Transfer resume(UUID transferId);
}
