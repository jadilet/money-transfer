package com.example.ledger.service;

import java.util.List;
import java.util.UUID;

/** Records transfers as double-entry and exposes ledger-derived reads. */
public interface LedgerService {

    /**
     * Record a completed transfer as a balanced journal entry (debit source, credit destination).
     * Idempotent on {@code transferId} — a redelivered event is a no-op.
     */
    void record(TransferCompletedEvent event);

    /** Balance for an account, derived by summing its postings (credits − debits). */
    LedgerViews.Balance balanceOf(UUID accountRef);

    /** All postings for an account, newest first — a statement. */
    List<LedgerViews.PostingLine> postingsOf(UUID accountRef);
}
