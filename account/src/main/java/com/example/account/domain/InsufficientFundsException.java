package com.example.account.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** The source account does not have enough balance for a debit. A business decline (maps to HTTP 422). */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId, BigDecimal balance, BigDecimal amount) {
        super("Account " + accountId + " has insufficient funds: balance " + balance + " < amount " + amount);
    }
}
