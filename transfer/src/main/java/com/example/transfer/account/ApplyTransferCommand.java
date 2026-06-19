package com.example.transfer.account;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Instruction for the account service to move money atomically: debit {@code fromAccountId}
 * and credit {@code toAccountId} in one transaction, enforcing overdraft on the source.
 * Carries the transfer's id and idempotency key so the account service can dedupe replays.
 */
public record ApplyTransferCommand(
        UUID transferId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        String idempotencyKey
) {
}
