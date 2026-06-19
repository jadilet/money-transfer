package com.example.transfer.domain;

/** Lifecycle of an outbox event: written PENDING in the business transaction, flipped to PUBLISHED once the relay sends it. */
public enum OutboxStatus {
    PENDING,
    PUBLISHED
}
