package com.example.transfer.outbox;

import com.example.transfer.domain.OutboxEvent;

/**
 * Sends an outbox event to the message broker. The default implementation logs; a Kafka-backed
 * implementation swaps in later behind configuration.
 */
public interface OutboxPublisher {

    /**
     * Publish the event. Must throw on failure so the relay leaves the row PENDING for retry.
     */
    void publish(OutboxEvent event);
}
