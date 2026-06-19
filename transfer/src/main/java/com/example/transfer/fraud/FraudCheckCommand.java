package com.example.transfer.fraud;

import java.math.BigDecimal;
import java.util.UUID;

/** What the fraud service needs to score a transfer. */
public record FraudCheckCommand(
        UUID transferId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        String idempotencyKey
) {
}
