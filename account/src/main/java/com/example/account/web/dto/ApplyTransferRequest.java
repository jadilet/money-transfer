package com.example.account.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Internal request from the transfer service to move money. Mirrors transfer's ApplyTransferCommand. */
public record ApplyTransferRequest(
        @NotNull UUID transferId,
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotBlank String idempotencyKey
) {
}
