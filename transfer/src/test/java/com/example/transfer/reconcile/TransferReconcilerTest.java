package com.example.transfer.reconcile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.transfer.service.TransferInProgressException;
import com.example.transfer.service.TransferService;
import com.example.transfer.service.TransferStore;
import com.example.transfer.service.TransferStore.StuckTransfer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit-tests the reconciler's decisions (resume vs expire vs skip) with controllable timestamps. */
class TransferReconcilerTest {

    private static final long MIN_AGE_MS = 30_000;     // 30s quiet before eligible
    private static final long MAX_AGE_MS = 3_600_000;  // 1h before giving up

    private final TransferService transferService = mock(TransferService.class);
    private final TransferStore store = mock(TransferStore.class);
    private final TransferReconciler reconciler =
            new TransferReconciler(transferService, store, MIN_AGE_MS, MAX_AGE_MS);

    @Test
    void resumesRecentlyStuckPending() {
        UUID id = UUID.randomUUID();
        StuckTransfer recent = new StuckTransfer(id, Instant.now().minusSeconds(120)); // < maxAge
        when(store.claimStuckPending(any(), anyInt())).thenReturn(List.of(recent));

        reconciler.reconcile();

        verify(transferService).resume(id);
        verify(store, never()).expire(any(), anyString());
    }

    @Test
    void expiresPendingOlderThanMaxAge() {
        UUID id = UUID.randomUUID();
        StuckTransfer ancient = new StuckTransfer(id, Instant.now().minusSeconds(7_200)); // 2h old > maxAge
        when(store.claimStuckPending(any(), anyInt())).thenReturn(List.of(ancient));

        reconciler.reconcile();

        verify(store).expire(eq(id), anyString());
        verify(transferService, never()).resume(any());
    }

    @Test
    void stillUnavailableTransferIsSkipped_andOthersStillProcessed() {
        UUID downId = UUID.randomUUID();
        UUID okId = UUID.randomUUID();
        StuckTransfer down = new StuckTransfer(downId, Instant.now().minusSeconds(120));
        StuckTransfer ok = new StuckTransfer(okId, Instant.now().minusSeconds(120));
        when(store.claimStuckPending(any(), anyInt())).thenReturn(List.of(down, ok));
        when(transferService.resume(downId)).thenThrow(new TransferInProgressException(downId, "still down"));

        reconciler.reconcile();

        verify(transferService).resume(downId);
        verify(transferService).resume(okId); // a failure on one does not stop the sweep
    }
}
