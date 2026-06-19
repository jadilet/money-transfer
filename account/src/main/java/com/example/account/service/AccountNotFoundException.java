package com.example.account.service;

import java.util.UUID;

/** An account referenced by a transfer does not exist. A terminal business problem (maps to HTTP 422). */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID accountId) {
        super("Account not found: " + accountId);
    }
}
