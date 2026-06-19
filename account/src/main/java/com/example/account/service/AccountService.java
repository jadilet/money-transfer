package com.example.account.service;

import java.util.List;
import java.util.UUID;

/** Application service for the account domain. */
public interface AccountService {

    /**
     * Apply a transfer: atomically debit the source and credit the destination. Idempotent on
     * {@code transferId} — a replay of an already-applied transfer is a no-op. Throws a business
     * exception (insufficient funds, currency mismatch, account not found/active) on decline.
     */
    void applyTransfer(ApplyTransferCommand command);

    /** List a client's accounts (cached). Throws {@link ClientNotFoundException} if the client is unknown. */
    List<AccountView> listAccounts(UUID clientId);
}
