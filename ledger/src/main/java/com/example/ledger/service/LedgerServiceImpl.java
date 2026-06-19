package com.example.ledger.service;

import com.example.ledger.domain.JournalEntry;
import com.example.ledger.domain.JournalEntryRepository;
import com.example.ledger.domain.LedgerAccount;
import com.example.ledger.domain.LedgerAccountRepository;
import com.example.ledger.domain.Posting;
import com.example.ledger.domain.PostingRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerServiceImpl implements LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerServiceImpl.class);

    private final LedgerAccountRepository ledgerAccounts;
    private final JournalEntryRepository journalEntries;
    private final PostingRepository postings;

    public LedgerServiceImpl(LedgerAccountRepository ledgerAccounts,
                             JournalEntryRepository journalEntries,
                             PostingRepository postings) {
        this.ledgerAccounts = ledgerAccounts;
        this.journalEntries = journalEntries;
        this.postings = postings;
    }

    @Override
    @Transactional
    public void record(TransferCompletedEvent event) {
        if (journalEntries.existsByTransferId(event.transferId())) {
            log.debug("Transfer {} already recorded; skipping", event.transferId());
            return;
        }

        LedgerAccount from = resolveAccount(event.fromAccountId(), event.currency());
        LedgerAccount to = resolveAccount(event.toAccountId(), event.currency());

        JournalEntry entry = journalEntries.save(
                JournalEntry.of(event.transferId(), "transfer " + event.transferId()));
        // Balanced double-entry: debit source, credit destination, equal amounts -> sum to zero.
        postings.save(Posting.debit(entry.getId(), from.getId(), event.amount(), event.currency()));
        postings.save(Posting.credit(entry.getId(), to.getId(), event.amount(), event.currency()));

        log.info("Recorded transfer {}: DEBIT {} / CREDIT {} amount={} {}",
                event.transferId(), from.getAccountRef(), to.getAccountRef(), event.amount(), event.currency());
    }

    @Override
    @Transactional(readOnly = true)
    public LedgerViews.Balance balanceOf(UUID accountRef) {
        LedgerAccount account = ledgerAccounts.findByAccountRef(accountRef)
                .orElseThrow(() -> new LedgerAccountNotFoundException(accountRef));
        BigDecimal balance = postings.findByLedgerAccountId(account.getId()).stream()
                .map(Posting::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LedgerViews.Balance(accountRef, account.getCurrency(), balance);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerViews.PostingLine> postingsOf(UUID accountRef) {
        LedgerAccount account = ledgerAccounts.findByAccountRef(accountRef)
                .orElseThrow(() -> new LedgerAccountNotFoundException(accountRef));
        return postings.findByLedgerAccountId(account.getId()).stream()
                .sorted(Comparator.comparing(Posting::getCreatedAt).reversed())
                .map(p -> new LedgerViews.PostingLine(
                        p.getJournalEntryId(), p.getDirection().name(), p.getAmount(), p.getCurrency(), p.getCreatedAt()))
                .toList();
    }

    /** Find the ledger account for an external account ref, creating it on first sight (race-safe). */
    private LedgerAccount resolveAccount(UUID accountRef, String currency) {
        return ledgerAccounts.findByAccountRef(accountRef).orElseGet(() -> {
            try {
                return ledgerAccounts.saveAndFlush(LedgerAccount.of(accountRef, currency));
            } catch (DataIntegrityViolationException e) {
                // Another partition created it concurrently; re-read.
                return ledgerAccounts.findByAccountRef(accountRef).orElseThrow(() -> e);
            }
        });
    }
}
