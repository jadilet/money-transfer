package com.example.transfer.outbox;

import com.example.transfer.domain.OutboxEvent;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes outbox events to Kafka (Redpanda). Active when {@code outbox.publisher=kafka}.
 *
 * <p>The record is keyed by the transfer id so all events for one transfer land on the same
 * partition (preserving per-transfer order), and the event type travels as a header. The send is
 * awaited: if it fails, this throws, so the relay leaves the row PENDING and retries next tick —
 * which is exactly the at-least-once guarantee the outbox is built on.
 */
@Component
@ConditionalOnProperty(name = "outbox.publisher", havingValue = "kafka")
public class KafkaOutboxPublisher implements OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public KafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                @Value("${outbox.topic:ledger.postings}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        log.info("Using Kafka outbox publisher, topic={}", topic);
    }

    @Override
    public void publish(OutboxEvent event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, event.getAggregateId().toString(), event.getPayload());
        record.headers().add(new RecordHeader("event-type", event.getType().getBytes(StandardCharsets.UTF_8)));
        try {
            kafkaTemplate.send(record).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted publishing outbox event " + event.getId(), e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to publish outbox event " + event.getId(), e.getCause());
        }
    }
}
