package com.example.ledger.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read models returned by the ledger's query API. */
public final class LedgerViews {

    private LedgerViews() {
    }

    /** A ledger-derived balance for an account — the sum of its postings. Used for reconciliation. */
    public record Balance(UUID accountRef, String currency, BigDecimal balance) {
    }

    /** One posting line, for statements/audit. */
    public record PostingLine(UUID journalEntryId, String direction, BigDecimal amount, String currency, Instant createdAt) {
    }
}
