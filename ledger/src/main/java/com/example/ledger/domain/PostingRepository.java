package com.example.ledger.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostingRepository extends JpaRepository<Posting, UUID> {

    List<Posting> findByLedgerAccountId(UUID ledgerAccountId);

    long countByJournalEntryId(UUID journalEntryId);
}
