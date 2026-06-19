package com.example.ledger.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ledger.domain.JournalEntryRepository;
import com.example.ledger.domain.LedgerAccountRepository;
import com.example.ledger.domain.PostingRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Verifies the consumer parses the transfer service's exact JSON payload and records it. */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class LedgerEventConsumerTest {

    @Autowired
    private LedgerEventConsumer consumer;
    @Autowired
    private JournalEntryRepository journalEntries;
    @Autowired
    private LedgerAccountRepository ledgerAccounts;
    @Autowired
    private PostingRepository postings;

    @BeforeEach
    void setUp() {
        postings.deleteAll();
        journalEntries.deleteAll();
        ledgerAccounts.deleteAll();
    }

    @Test
    void parsesTransferCompletedPayload_andRecordsDoubleEntry() {
        // The exact shape the transfer service publishes (amount as a string; extra fields ignored).
        String payload = """
                {"transferId":"33333333-3333-3333-3333-333333333333",
                 "fromAccountId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                 "toAccountId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                 "amount":"250.0000","currency":"KGS","status":"COMPLETED",
                 "occurredAt":"2026-06-18T18:54:56.724902Z"}
                """;

        consumer.onMessage(payload);

        assertThat(journalEntries.count()).isEqualTo(1);
        assertThat(postings.count()).isEqualTo(2);
        assertThat(ledgerAccounts.count()).isEqualTo(2);
        assertThat(journalEntries.existsByTransferId(
                UUID.fromString("33333333-3333-3333-3333-333333333333"))).isTrue();
    }
}
