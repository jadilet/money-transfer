package com.example.transfer.service;

import com.example.transfer.account.AccountClient;
import com.example.transfer.account.AccountDeclinedException;
import com.example.transfer.account.AccountException;
import com.example.transfer.account.ApplyTransferCommand;
import com.example.transfer.domain.Transfer;
import com.example.transfer.domain.TransferRepository;
import com.example.transfer.fraud.FraudCheckCommand;
import com.example.transfer.fraud.FraudClient;
import com.example.transfer.fraud.FraudDecision;
import com.example.transfer.fraud.FraudException;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the transfer saga. Transfer holds state only; the authoritative money-move lives in
 * the account service. The flow is: dedupe → persist PENDING → fraud check (sync) → account move
 * (sync) → finalize.
 *
 * <p>Key distinction: a downstream <em>decision</em> (fraud rejection, insufficient funds) is a
 * terminal outcome (BLOCKED / FAILED). A downstream <em>outage</em> (fraud or account unreachable,
 * circuit open, timeout) is transient — the transfer is left PENDING and a
 * {@link TransferInProgressException} is raised, so a same-key retry resumes it rather than the
 * client being dead-ended on a terminal state.
 */
@Service
public class TransferServiceImpl implements TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferServiceImpl.class);

    private final TransferRepository transfers;
    private final TransferStore store;
    private final FraudClient fraudClient;
    private final AccountClient accountClient;
    private final boolean fraudFailOpen;

    public TransferServiceImpl(TransferRepository transfers,
                               TransferStore store,
                               FraudClient fraudClient,
                               AccountClient accountClient,
                               @Value("${fraud.fail-open:false}") boolean fraudFailOpen) {
        this.transfers = transfers;
        this.store = store;
        this.fraudClient = fraudClient;
        this.accountClient = accountClient;
        this.fraudFailOpen = fraudFailOpen;
    }

    @Override
    public Transfer create(CreateTransferCommand command) {
        validate(command);
        String currency = command.currency().toUpperCase(Locale.ROOT);

        var existing = transfers.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            Transfer transfer = existing.get();
            if (transfer.getStatus().isTerminal()) {
                log.debug("Idempotent hit (terminal {}) for key={}", transfer.getStatus(), command.idempotencyKey());
                return transfer;
            }
            // Non-terminal: a prior attempt was interrupted by a downstream outage. Resume it.
            log.debug("Resuming non-terminal transfer {} for key={}", transfer.getId(), command.idempotencyKey());
            return process(transfer);
        }

        TransferStore.PersistResult persisted = store.createPending(command, currency);
        if (!persisted.created()) {
            // Concurrent create with the same key won the race; it owns processing.
            return persisted.transfer();
        }
        return process(persisted.transfer());
    }

    @Override
    @Transactional(readOnly = true)
    public Transfer getById(UUID id) {
        return transfers.findById(id).orElseThrow(() -> new TransferNotFoundException(id));
    }

    @Override
    public Transfer resume(UUID transferId) {
        Transfer transfer = transfers.findById(transferId).orElseThrow(() -> new TransferNotFoundException(transferId));
        if (transfer.getStatus().isTerminal()) {
            return transfer;
        }
        return process(transfer);
    }

    /** Runs the fraud → account → finalize sequence. Idempotent: safe to re-run on a PENDING transfer. */
    private Transfer process(Transfer transfer) {
        UUID id = transfer.getId();

        FraudDecision decision = screenForFraud(transfer);
        if (!decision.approved()) {
            return store.block(id, "fraud rejected: " + decision.reason());
        }

        try {
            accountClient.applyTransfer(new ApplyTransferCommand(
                    id,
                    transfer.getFromAccountId(),
                    transfer.getToAccountId(),
                    transfer.getAmount(),
                    transfer.getCurrency(),
                    transfer.getIdempotencyKey()));
            return store.complete(id);
        } catch (AccountDeclinedException e) {
            // Terminal business decline (insufficient funds, account not found, currency mismatch, ...).
            // Record the account's actual reason — not a hardcoded one.
            log.info("Transfer {} declined by account: {}", id, e.getMessage());
            return store.fail(id, e.getMessage());
        } catch (AccountException e) {
            // Transient outage. Leave PENDING; the account call is idempotent on transferId, so a
            // same-key retry can safely re-apply (and gets the prior result if it already happened).
            log.warn("Account move unavailable for transfer {}; left PENDING: {}", id, e.getMessage());
            throw new TransferInProgressException(id, "account service temporarily unavailable");
        }
    }

    private FraudDecision screenForFraud(Transfer transfer) {
        try {
            return fraudClient.check(new FraudCheckCommand(
                    transfer.getId(),
                    transfer.getFromAccountId(),
                    transfer.getToAccountId(),
                    transfer.getAmount(),
                    transfer.getCurrency(),
                    transfer.getIdempotencyKey()));
        } catch (FraudException e) {
            if (fraudFailOpen) {
                log.warn("Fraud check unavailable for transfer {} (fail-open); proceeding: {}",
                        transfer.getId(), e.getMessage());
                return FraudDecision.approve();
            }
            // Fail-closed: we couldn't screen it, so we don't move money — but this is an outage, not
            // a rejection. Leave PENDING so a same-key retry resumes once fraud recovers.
            log.warn("Fraud check unavailable for transfer {} (fail-closed); left PENDING: {}",
                    transfer.getId(), e.getMessage());
            throw new TransferInProgressException(transfer.getId(), "fraud check temporarily unavailable");
        }
    }

    private void validate(CreateTransferCommand command) {
        if (command.fromAccountId().equals(command.toAccountId())) {
            throw new InvalidTransferException("fromAccountId and toAccountId must differ");
        }
    }
}
