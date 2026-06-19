package com.example.ledger.kafka;

import com.example.ledger.service.LedgerService;
import com.example.ledger.service.TransferCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes transfer events from {@code ledger.postings} and records each as double-entry. The
 * group id means the topic is processed once per consumer group; recording is idempotent on
 * transferId, so Kafka's at-least-once redelivery is safe.
 */
@Component
public class LedgerEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LedgerEventConsumer.class);

    private final LedgerService ledgerService;
    private final ObjectMapper objectMapper;

    public LedgerEventConsumer(LedgerService ledgerService, ObjectMapper objectMapper) {
        this.ledgerService = ledgerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${ledger.topic:ledger.postings}", groupId = "${spring.kafka.consumer.group-id:ledger}")
    public void onMessage(String payload) {
        TransferCompletedEvent event = objectMapper.readValue(payload, TransferCompletedEvent.class);
        ledgerService.record(event);
    }
}
