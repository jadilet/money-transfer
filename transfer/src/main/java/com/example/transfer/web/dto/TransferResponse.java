package com.example.transfer.web.dto;

import com.example.transfer.domain.Transfer;
import com.example.transfer.domain.TransferStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** What the API returns for a transfer. */
public record TransferResponse(
        UUID id,
        TransferStatus status,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static TransferResponse from(Transfer t) {
        return new TransferResponse(
                t.getId(),
                t.getStatus(),
                t.getFromAccountId(),
                t.getToAccountId(),
                t.getAmount(),
                t.getCurrency(),
                t.getFailureReason(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
