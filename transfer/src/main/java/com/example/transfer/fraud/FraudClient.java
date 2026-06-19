package com.example.transfer.fraud;

/**
 * Boundary to the fraud service. This is a synchronous, latency-sensitive call on the critical
 * path — it must be fast and protected by a timeout and circuit breaker.
 */
public interface FraudClient {

    /**
     * Score a transfer for fraud.
     *
     * @throws FraudException if the service is unreachable or errors (caller applies its fail policy)
     */
    FraudDecision check(FraudCheckCommand command);
}
