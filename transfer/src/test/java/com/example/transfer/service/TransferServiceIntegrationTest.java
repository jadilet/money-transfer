package com.example.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.transfer.account.AccountClient;
import com.example.transfer.account.AccountException;
import com.example.transfer.account.InsufficientFundsException;
import com.example.transfer.domain.OutboxEventRepository;
import com.example.transfer.domain.OutboxStatus;
import com.example.transfer.domain.Transfer;
import com.example.transfer.domain.TransferRepository;
import com.example.transfer.domain.TransferStatus;
import com.example.transfer.fraud.FraudClient;
import com.example.transfer.fraud.FraudDecision;
import com.example.transfer.fraud.FraudException;
import com.example.transfer.outbox.OutboxPublisher;
import com.example.transfer.outbox.OutboxRelay;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Exercises the full saga against a real H2 database and real persistence, with only the external
 * boundaries (fraud, account, outbox publisher) mocked. The relay interval is set very high so the
 * scheduler never fires mid-test; we drive the relay explicitly where needed.
 */
@SpringBootTest(properties = {"outbox.relay.interval-ms=3600000", "reconcile.interval-ms=3600000"})
class TransferServiceIntegrationTest {

    @Autowired
    private TransferService service;
    @Autowired
    private TransferRepository transfers;
    @Autowired
    private OutboxEventRepository outbox;
    @Autowired
    private OutboxRelay relay;

    @MockitoBean
    private FraudClient fraudClient;
    @MockitoBean
    private AccountClient accountClient;
    @MockitoBean
    private OutboxPublisher publisher;

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        outbox.deleteAll();
        transfers.deleteAll();
        when(fraudClient.check(any())).thenReturn(FraudDecision.approve());
    }

    private CreateTransferCommand command(String key, String amount) {
        return new CreateTransferCommand(key, from, to, new BigDecimal(amount), "kgs");
    }

    @Test
    void completesAndWritesOutboxEvent() {
        Transfer result = service.create(command("k1", "100"));

        assertThat(result.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.getCurrency()).isEqualTo("KGS"); // normalized to upper case
        verify(accountClient, times(1)).applyTransfer(any());
        assertThat(outbox.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .singleElement()
                .satisfies(e -> assertThat(e.getType()).isEqualTo("TransferCompleted"));
    }

    @Test
    void blockedByFraud_doesNotMoveMoneyOrEmitEvent() {
        when(fraudClient.check(any())).thenReturn(FraudDecision.reject("risky"));

        Transfer result = service.create(command("k2", "100"));

        assertThat(result.getStatus()).isEqualTo(TransferStatus.BLOCKED);
        verify(accountClient, never()).applyTransfer(any());
        assertThat(outbox.count()).isZero();
    }

    @Test
    void accountFailure_marksFailed_andEmitsNoEvent() {
        doThrow(new InsufficientFundsException("insufficient funds"))
                .when(accountClient).applyTransfer(any());

        Transfer result = service.create(command("k3", "100"));

        assertThat(result.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(result.getFailureReason()).contains("insufficient");
        assertThat(outbox.count()).isZero();
    }

    @Test
    void fraudUnavailable_failsClosed_leavesPendingAndSignalsRetry() {
        when(fraudClient.check(any())).thenThrow(new FraudException("fraud service down"));

        CreateTransferCommand cmd = command("k4", "100");
        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(TransferInProgressException.class);

        // Not terminally blocked — left PENDING so a same-key retry can resume it.
        Transfer pending = transfers.findByIdempotencyKey("k4").orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(TransferStatus.PENDING);
        verify(accountClient, never()).applyTransfer(any());
    }

    @Test
    void accountUnavailable_leavesPendingAndSignalsRetry() {
        doThrow(new AccountException("connection refused")).when(accountClient).applyTransfer(any());

        CreateTransferCommand cmd = command("k6", "100");
        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(TransferInProgressException.class);

        Transfer pending = transfers.findByIdempotencyKey("k6").orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void retryWithSameKey_resumesPendingTransfer_toCompletion() {
        // First attempt: fraud is down, so the transfer is left PENDING.
        when(fraudClient.check(any())).thenThrow(new FraudException("down"));
        CreateTransferCommand cmd = command("resume", "100");
        assertThatThrownBy(() -> service.create(cmd)).isInstanceOf(TransferInProgressException.class);
        assertThat(transfers.findByIdempotencyKey("resume").orElseThrow().getStatus())
                .isEqualTo(TransferStatus.PENDING);

        // Fraud recovers; retrying with the SAME key resumes the existing transfer and completes it.
        // (doReturn form: re-stubbing via when(check(...)) would invoke the still-throwing stub.)
        doReturn(FraudDecision.approve()).when(fraudClient).check(any());
        Transfer result = service.create(cmd);

        assertThat(result.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        verify(accountClient, times(1)).applyTransfer(any());
        assertThat(transfers.count()).isEqualTo(1); // same transfer resumed, not a new one
    }

    @Test
    void idempotentReplay_returnsSameTransfer_andRunsSideEffectsOnce() {
        Transfer first = service.create(command("dup", "100"));
        Transfer second = service.create(command("dup", "100"));

        assertThat(second.getId()).isEqualTo(first.getId());
        verify(accountClient, times(1)).applyTransfer(any());
        assertThat(transfers.count()).isEqualTo(1);
    }

    @Test
    void selfTransfer_isRejected() {
        CreateTransferCommand self = new CreateTransferCommand("k5", from, from, new BigDecimal("10"), "KGS");

        assertThatThrownBy(() -> service.create(self))
                .isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void relay_publishesPendingEvents_andMarksThemPublished() {
        service.create(command("relay", "100"));
        assertThat(outbox.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).hasSize(1);

        relay.publishPending();

        verify(publisher, times(1)).publish(any());
        assertThat(outbox.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)).isEmpty();
    }
}
