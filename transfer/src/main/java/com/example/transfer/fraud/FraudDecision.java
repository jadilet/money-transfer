package com.example.transfer.fraud;

/** Outcome of a fraud check. {@code reason} explains a rejection (null when approved). */
public record FraudDecision(boolean approved, String reason) {

    public static FraudDecision approve() {
        return new FraudDecision(true, null);
    }

    public static FraudDecision reject(String reason) {
        return new FraudDecision(false, reason);
    }
}
