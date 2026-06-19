package com.example.transfer.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Incoming request to move money. The {@code idempotencyKey} is client-supplied and
 * makes retries safe: the same key always maps to the same transfer.
 */
public record TransferRequest(
        @NotBlank String idempotencyKey,
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency
) {
}
