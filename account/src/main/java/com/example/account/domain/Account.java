package com.example.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An account with an authoritative balance. Balance changes go through {@link #debit} / {@link #credit},
 * which enforce the invariants (account active, no overdraft). The {@code version} column gives optimistic
 * locking as a second line of defense; the money-move also pessimistically locks the rows it touches.
 */
@Entity
@Table(name = "accounts", indexes = @Index(name = "ix_accounts_client_id", columnList = "clientId"))
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID clientId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;

    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Account() {
        // for JPA
    }

    private Account(UUID clientId, String currency, BigDecimal openingBalance) {
        this.id = UUID.randomUUID();
        this.clientId = clientId;
        this.currency = currency;
        this.balance = openingBalance;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Account open(UUID clientId, String currency, BigDecimal openingBalance) {
        return new Account(clientId, currency, openingBalance);
    }

    public void debit(BigDecimal amount) {
        ensureActive();
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(id, balance, amount);
        }
        this.balance = balance.subtract(amount);
        this.updatedAt = Instant.now();
    }

    public void credit(BigDecimal amount) {
        ensureActive();
        this.balance = balance.add(amount);
        this.updatedAt = Instant.now();
    }

    public void freeze() {
        this.status = AccountStatus.FROZEN;
        this.updatedAt = Instant.now();
    }

    private void ensureActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(id, status);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
