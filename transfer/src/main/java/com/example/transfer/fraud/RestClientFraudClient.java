package com.example.transfer.fraud;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real HTTP client to the fraud service, wrapped in resilience policies. Latency-sensitive, so the
 * retry budget is small; the circuit breaker sheds load when the service is failing. A
 * {@link FraudException} (unreachable / circuit open) is left for the orchestrator's fail policy.
 *
 * <p>Active when {@code fraud.client=rest}.
 */
@Component
@ConditionalOnProperty(name = "fraud.client", havingValue = "rest")
public class RestClientFraudClient implements FraudClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientFraudClient.class);

    private final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public RestClientFraudClient(RestClient.Builder builder,
                                 @Value("${fraud.base-url}") String baseUrl,
                                 @Qualifier("fraudRetry") Retry retry,
                                 @Qualifier("fraudCircuitBreaker") CircuitBreaker circuitBreaker) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
        log.info("Using REST fraud client at {}", baseUrl);
    }

    @Override
    public FraudDecision check(FraudCheckCommand command) {
        Supplier<FraudDecision> resilient = CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry, () -> doCheck(command)));
        try {
            return resilient.get();
        } catch (CallNotPermittedException e) {
            throw new FraudException("fraud service unavailable (circuit open)", e);
        }
    }

    private FraudDecision doCheck(FraudCheckCommand command) {
        try {
            FraudCheckResponse response = restClient.post()
                    .uri("/internal/fraud-checks")
                    .body(command)
                    .retrieve()
                    .body(FraudCheckResponse.class);
            if (response == null) {
                throw new FraudException("empty fraud response");
            }
            return response.approved() ? FraudDecision.approve() : FraudDecision.reject(response.reason());
        } catch (FraudException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FraudException("fraud call failed: " + e.getMessage(), e);
        }
    }

    /** Wire response from the fraud service. */
    record FraudCheckResponse(boolean approved, String reason) {
    }
}
