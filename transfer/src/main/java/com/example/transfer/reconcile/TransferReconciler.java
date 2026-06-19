package com.example.transfer.reconcile;

import com.example.transfer.service.TransferInProgressException;
import com.example.transfer.service.TransferService;
import com.example.transfer.service.TransferStore;
import com.example.transfer.service.TransferStore.StuckTransfer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net for transfers stuck in PENDING — typically because a downstream outage interrupted the
 * saga and the client never retried. Each tick it claims PENDING transfers quiet for at least
 * {@code minAge} (claiming locks them with SKIP LOCKED so concurrent reconcilers take disjoint work),
 * then, outside the lock:
 *
 * <ul>
 *   <li>if older than {@code maxAge}, gives up and marks it EXPIRED (no money moved);
 *   <li>otherwise resumes the saga — safe because every downstream call is idempotent on the transfer id.
 * </ul>
 */
@Component
public class TransferReconciler {

    private static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(TransferReconciler.class);

    private final TransferService transferService;
    private final TransferStore store;
    private final Duration minAge;
    private final Duration maxAge;

    public TransferReconciler(TransferService transferService,
                              TransferStore store,
                              @Value("${reconcile.min-age-ms:30000}") long minAgeMs,
                              @Value("${reconcile.max-age-ms:3600000}") long maxAgeMs) {
        this.transferService = transferService;
        this.store = store;
        this.minAge = Duration.ofMillis(minAgeMs);
        this.maxAge = Duration.ofMillis(maxAgeMs);
    }

    @Scheduled(fixedDelayString = "${reconcile.interval-ms:10000}")
    public void reconcile() {
        Instant now = Instant.now();
        List<StuckTransfer> claimed = store.claimStuckPending(now.minus(minAge), BATCH_SIZE);
        if (claimed.isEmpty()) {
            return;
        }

        Instant expireBefore = now.minus(maxAge);
        for (StuckTransfer transfer : claimed) {
            if (transfer.createdAt().isBefore(expireBefore)) {
                log.warn("Expiring transfer {} stuck PENDING since {}", transfer.id(), transfer.createdAt());
                store.expire(transfer.id(), "expired: not completed within max age");
                continue;
            }
            try {
                transferService.resume(transfer.id());
                log.info("Reconciled transfer {}", transfer.id());
            } catch (TransferInProgressException e) {
                // Downstream still unavailable; leave PENDING and try again next tick.
                log.debug("Transfer {} still cannot complete: {}", transfer.id(), e.getMessage());
            } catch (RuntimeException e) {
                log.warn("Reconciliation of transfer {} failed: {}", transfer.id(), e.getMessage());
            }
        }
    }
}
