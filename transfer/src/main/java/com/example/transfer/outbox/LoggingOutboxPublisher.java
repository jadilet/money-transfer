package com.example.transfer.outbox;

import com.example.transfer.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default publisher: logs the event instead of sending it to a broker, so the service runs without
 * Kafka during development. Active by default; set {@code outbox.publisher=kafka} for the real one.
 */
@Component
@ConditionalOnProperty(name = "outbox.publisher", havingValue = "log", matchIfMissing = true)
public class LoggingOutboxPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingOutboxPublisher.class);

    @Override
    public void publish(OutboxEvent event) {
        log.info("[outbox -> log] type={} aggregateId={} payload={}",
                event.getType(), event.getAggregateId(), event.getPayload());
    }
}
