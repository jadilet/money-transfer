package com.example.account.domain;

import java.util.UUID;

/** An account involved in a transfer is not ACTIVE (frozen/closed). A business decline (maps to HTTP 422). */
public class AccountNotActiveException extends RuntimeException {

    public AccountNotActiveException(UUID accountId, AccountStatus status) {
        super("Account " + accountId + " is not active (status=" + status + ")");
    }
}
