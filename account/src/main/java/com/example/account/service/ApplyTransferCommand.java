package com.example.account.service;

import java.math.BigDecimal;
import java.util.UUID;

/** Instruction to move money atomically: debit {@code fromAccountId}, credit {@code toAccountId}. */
public record ApplyTransferCommand(
        UUID transferId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        String idempotencyKey
) {
}
