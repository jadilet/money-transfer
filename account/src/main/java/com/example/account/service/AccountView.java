package com.example.account.service;

import com.example.account.domain.Account;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read model for an account. A record (not the JPA entity) so it's safe to cache — including in
 * Redis, where it must serialize cleanly.
 */
public record AccountView(
        UUID id,
        UUID clientId,
        String currency,
        BigDecimal balance,
        String status
) implements Serializable {

    public static AccountView from(Account account) {
        return new AccountView(
                account.getId(),
                account.getClientId(),
                account.getCurrency(),
                account.getBalance(),
                account.getStatus().name());
    }
}
