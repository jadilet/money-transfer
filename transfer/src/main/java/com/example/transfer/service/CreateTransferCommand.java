package com.example.transfer.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal command to create a transfer, decoupled from the web layer's DTO so the
 * service is not tied to HTTP shapes.
 */
public record CreateTransferCommand(
        String idempotencyKey,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency
) {
}
