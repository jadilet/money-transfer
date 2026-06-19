package com.example.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ledger.domain.JournalEntryRepository;
import com.example.ledger.domain.LedgerAccountRepository;
import com.example.ledger.domain.Posting;
import com.example.ledger.domain.PostingRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class LedgerServiceTest {

    @Autowired
    private LedgerService service;
    @Autowired
    private LedgerAccountRepository ledgerAccounts;
    @Autowired
    private JournalEntryRepository journalEntries;
    @Autowired
    private PostingRepository postings;

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        postings.deleteAll();
        journalEntries.deleteAll();
        ledgerAccounts.deleteAll();
    }

    private TransferCompletedEvent event(UUID transferId, String amount) {
        return new TransferCompletedEvent(transferId, from, to, new BigDecimal(amount), "KGS");
    }

    @Test
    void recordsBalancedDoubleEntry() {
        service.record(event(UUID.randomUUID(), "100"));

        assertThat(journalEntries.count()).isEqualTo(1);
        assertThat(postings.count()).isEqualTo(2);
        assertThat(ledgerAccounts.count()).isEqualTo(2);

        // Derived balances: source debited, destination credited.
        assertThat(service.balanceOf(from).balance()).isEqualByComparingTo("-100");
        assertThat(service.balanceOf(to).balance()).isEqualByComparingTo("100");

        // The books balance: every posting sums to zero.
        BigDecimal sum = postings.findAll().stream()
                .map(Posting::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("0");
    }

    @Test
    void redeliveredEvent_isRecordedOnce() {
        UUID transferId = UUID.randomUUID();
        service.record(event(transferId, "100"));
        service.record(event(transferId, "100")); // Kafka redelivery

        assertThat(journalEntries.count()).isEqualTo(1);
        assertThat(postings.count()).isEqualTo(2);
        assertThat(service.balanceOf(from).balance()).isEqualByComparingTo("-100");
    }

    @Test
    void multipleTransfers_accumulateInDerivedBalance() {
        service.record(event(UUID.randomUUID(), "100"));
        service.record(event(UUID.randomUUID(), "50"));

        assertThat(service.balanceOf(from).balance()).isEqualByComparingTo("-150");
        assertThat(service.balanceOf(to).balance()).isEqualByComparingTo("150");
    }

    @Test
    void balanceOf_unknownAccount_throws() {
        assertThatThrownBy(() -> service.balanceOf(UUID.randomUUID()))
                .isInstanceOf(LedgerAccountNotFoundException.class);
    }
}
