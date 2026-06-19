package com.example.transfer.outbox;

import com.example.transfer.domain.OutboxEvent;
import com.example.transfer.domain.OutboxEventRepository;
import com.example.transfer.domain.OutboxStatus;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the transactional outbox: on each tick it reads PENDING events, publishes them, and marks
 * them PUBLISHED. Publishing and the status flip share one transaction, so a publish failure leaves
 * the row PENDING to be retried on the next tick (at-least-once delivery).
 *
 * <p>Single-instance safe as written. For multiple instances, switch the read to a
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} query so two relays don't grab the same rows.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final OutboxPublisher publisher;

    public OutboxRelay(OutboxEventRepository outbox, OutboxPublisher publisher) {
        this.outbox = outbox;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outbox.findBatchForPublishing(OutboxStatus.PENDING, PageRequest.of(0, 100));
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent event : batch) {
            publisher.publish(event);
            event.markPublished();
        }
        log.debug("Published {} outbox event(s)", batch.size());
    }
}
