package com.example.ledger.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The slice of the transfer service's {@code TransferCompleted} event the ledger needs. Unknown
 * fields in the payload (status, occurredAt) are ignored by the JSON mapper.
 */
public record TransferCompletedEvent(
        UUID transferId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency
) {
}
