package com.example.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The ledger's record of an account it posts to. {@code accountRef} is the account service's
 * account id (a logical reference across services, not a foreign key). Balances are not stored
 * here — they are derived by summing this account's postings.
 */
@Entity
@Table(name = "ledger_accounts",
        indexes = @Index(name = "ux_ledger_accounts_account_ref", columnList = "accountRef", unique = true))
public class LedgerAccount {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID accountRef;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerAccount() {
        // for JPA
    }

    private LedgerAccount(UUID accountRef, String currency) {
        this.id = UUID.randomUUID();
        this.accountRef = accountRef;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public static LedgerAccount of(UUID accountRef, String currency) {
        return new LedgerAccount(accountRef, currency);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountRef() {
        return accountRef;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
