package com.example.ledger.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    /** Consumer-side idempotency: has this transfer already been recorded? */
    boolean existsByTransferId(UUID transferId);
}
