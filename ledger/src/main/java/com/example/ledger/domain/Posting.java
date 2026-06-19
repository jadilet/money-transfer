package com.example.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of a journal entry: a debit or credit of an amount against a ledger account. Immutable
 * once written — the ledger is append-only.
 */
@Entity
@Table(name = "postings", indexes = {
        @Index(name = "ix_postings_journal_entry", columnList = "journalEntryId"),
        @Index(name = "ix_postings_ledger_account", columnList = "ledgerAccountId")
})
public class Posting {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID journalEntryId;

    @Column(nullable = false, updatable = false)
    private UUID ledgerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Direction direction;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Posting() {
        // for JPA
    }

    private Posting(UUID journalEntryId, UUID ledgerAccountId, Direction direction, BigDecimal amount, String currency) {
        this.id = UUID.randomUUID();
        this.journalEntryId = journalEntryId;
        this.ledgerAccountId = ledgerAccountId;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public static Posting debit(UUID journalEntryId, UUID ledgerAccountId, BigDecimal amount, String currency) {
        return new Posting(journalEntryId, ledgerAccountId, Direction.DEBIT, amount, currency);
    }

    public static Posting credit(UUID journalEntryId, UUID ledgerAccountId, BigDecimal amount, String currency) {
        return new Posting(journalEntryId, ledgerAccountId, Direction.CREDIT, amount, currency);
    }

    /** Signed contribution to the account balance: credits add, debits subtract. */
    public BigDecimal signedAmount() {
        return direction == Direction.CREDIT ? amount : amount.negate();
    }

    public UUID getId() {
        return id;
    }

    public UUID getJournalEntryId() {
        return journalEntryId;
    }

    public UUID getLedgerAccountId() {
        return ledgerAccountId;
    }

    public Direction getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
