package com.example.transfer.resilience;

import com.example.transfer.account.AccountDeclinedException;
import com.example.transfer.account.AccountException;
import com.example.transfer.fraud.FraudException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resilience policies for the synchronous outbound calls. Built programmatically from the
 * Resilience4j core libraries (no Spring Boot AOP starter), so it is independent of Boot
 * auto-configuration.
 *
 * <p>Account: retry transient failures with exponential backoff, but never retry or trip the
 * breaker on a business decline ({@link InsufficientFundsException}). Fraud: latency-sensitive,
 * so at most one retry; the breaker sheds load when the fraud service is failing.
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public Retry accountRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(100), 2.0))
                .retryExceptions(AccountException.class)
                .ignoreExceptions(AccountDeclinedException.class)
                .build();
        return Retry.of("account", config);
    }

    @Bean
    public CircuitBreaker accountCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .ignoreExceptions(AccountDeclinedException.class)
                .build();
        return CircuitBreaker.of("account", config);
    }

    @Bean
    public Retry fraudRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(2)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(50), 2.0))
                .retryExceptions(FraudException.class)
                .build();
        return Retry.of("fraud", config);
    }

    @Bean
    public CircuitBreaker fraudCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .build();
        return CircuitBreaker.of("fraud", config);
    }
}
