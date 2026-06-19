package com.example.transfer.service;

import com.example.transfer.domain.OutboxEvent;
import com.example.transfer.domain.OutboxEventRepository;
import com.example.transfer.domain.Transfer;
import com.example.transfer.domain.TransferRepository;
import com.example.transfer.domain.TransferStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns all transactional writes for a transfer, keeping clear transaction boundaries out of the
 * orchestrator. Crucially, {@link #complete(UUID)} writes the outbox event in the same transaction
 * as the status change, so "completed" and "event queued" commit atomically.
 */
@Component
public class TransferStore {

    private static final String EVENT_TRANSFER_COMPLETED = "TransferCompleted";

    private final TransferRepository transfers;
    private final OutboxEventRepository outbox;

    public TransferStore(TransferRepository transfers, OutboxEventRepository outbox) {
        this.transfers = transfers;
        this.outbox = outbox;
    }

    /** Result of attempting to create a transfer: {@code created} is false if an identical key already existed. */
    public record PersistResult(Transfer transfer, boolean created) {
    }

    /** A reconciler-claimed transfer: just the bits the reconciler needs after the claim transaction. */
    public record StuckTransfer(UUID id, Instant createdAt) {
    }

    /**
     * Claim a batch of stuck PENDING transfers for reconciliation: lock them with SKIP LOCKED and
     * bump their activity timestamp, so other reconcilers (or the next tick) won't grab the same
     * rows. The lock is released when this short transaction commits — processing happens afterward,
     * outside the lock.
     */
    @Transactional
    public List<StuckTransfer> claimStuckPending(Instant updatedBefore, int limit) {
        List<Transfer> rows = transfers.findStuckForUpdate(
                TransferStatus.PENDING, updatedBefore, PageRequest.of(0, limit));
        List<StuckTransfer> claimed = new ArrayList<>(rows.size());
        for (Transfer transfer : rows) {
            transfer.touch();
            claimed.add(new StuckTransfer(transfer.getId(), transfer.getCreatedAt()));
        }
        return claimed;
    }

    @Transactional
    public PersistResult createPending(CreateTransferCommand command, String currency) {
        Transfer transfer = Transfer.pending(
                command.idempotencyKey(),
                command.fromAccountId(),
                command.toAccountId(),
                command.amount(),
                currency);
        try {
            return new PersistResult(transfers.saveAndFlush(transfer), true);
        } catch (DataIntegrityViolationException e) {
            // Concurrent request with the same idempotency key won the race; hand back its row.
            Transfer existing = transfers.findByIdempotencyKey(command.idempotencyKey()).orElseThrow(() -> e);
            return new PersistResult(existing, false);
        }
    }

    @Transactional
    public Transfer complete(UUID transferId) {
        Transfer transfer = load(transferId);
        if (transfer.getStatus().isTerminal()) {
            return transfer; // already finalized (e.g. a concurrent retry/reconcile won) — no duplicate event
        }
        transfer.markCompleted();
        transfers.save(transfer);
        outbox.save(OutboxEvent.of(EVENT_TRANSFER_COMPLETED, transfer.getId(), completedPayload(transfer)));
        return transfer;
    }

    @Transactional
    public Transfer fail(UUID transferId, String reason) {
        Transfer transfer = load(transferId);
        if (transfer.getStatus().isTerminal()) {
            return transfer;
        }
        transfer.markFailed(reason);
        return transfers.save(transfer);
    }

    @Transactional
    public Transfer block(UUID transferId, String reason) {
        Transfer transfer = load(transferId);
        if (transfer.getStatus().isTerminal()) {
            return transfer;
        }
        transfer.markBlocked(reason);
        return transfers.save(transfer);
    }

    @Transactional
    public Transfer expire(UUID transferId, String reason) {
        Transfer transfer = load(transferId);
        if (transfer.getStatus().isTerminal()) {
            return transfer;
        }
        transfer.markExpired(reason);
        return transfers.save(transfer);
    }

    private Transfer load(UUID transferId) {
        return transfers.findById(transferId).orElseThrow(() -> new TransferNotFoundException(transferId));
    }

    private String completedPayload(Transfer transfer) {
        // All values are controlled (UUIDs, a decimal, a 3-letter currency, an enum, an ISO instant)
        // and contain no characters that need JSON escaping.
        return "{"
                + "\"transferId\":\"" + transfer.getId() + "\","
                + "\"fromAccountId\":\"" + transfer.getFromAccountId() + "\","
                + "\"toAccountId\":\"" + transfer.getToAccountId() + "\","
                + "\"amount\":\"" + transfer.getAmount().toPlainString() + "\","
                + "\"currency\":\"" + transfer.getCurrency() + "\","
                + "\"status\":\"" + transfer.getStatus().name() + "\","
                + "\"occurredAt\":\"" + transfer.getUpdatedAt() + "\""
                + "}";
    }
}
