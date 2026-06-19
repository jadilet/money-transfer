package com.example.ledger.service;

import java.util.UUID;

/** No ledger account exists for the given account reference. Maps to HTTP 404. */
public class LedgerAccountNotFoundException extends RuntimeException {

    public LedgerAccountNotFoundException(UUID accountRef) {
        super("No ledger account for account ref: " + accountRef);
    }
}
