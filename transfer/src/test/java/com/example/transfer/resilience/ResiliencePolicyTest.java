package com.example.transfer.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.transfer.account.AccountException;
import com.example.transfer.account.InsufficientFundsException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Verifies the configured account retry and circuit-breaker policies behave as intended. */
class ResiliencePolicyTest {

    private final ResilienceConfig config = new ResilienceConfig();

    @Test
    void retriesTransientFailures_thenSucceeds() {
        Retry retry = config.accountRetry();
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> supplier = Retry.decorateSupplier(retry, () -> {
            if (calls.incrementAndGet() < 3) {
                throw new AccountException("transient");
            }
            return "ok";
        });

        assertThat(supplier.get()).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3); // failed twice, succeeded on the third attempt
    }

    @Test
    void exhaustsRetries_thenThrows() {
        Retry retry = config.accountRetry();
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> supplier = Retry.decorateSupplier(retry, () -> {
            calls.incrementAndGet();
            throw new AccountException("always down");
        });

        assertThatThrownBy(supplier::get).isInstanceOf(AccountException.class);
        assertThat(calls.get()).isEqualTo(3); // maxAttempts
    }

    @Test
    void doesNotRetryBusinessDecline() {
        Retry retry = config.accountRetry();
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> supplier = Retry.decorateSupplier(retry, () -> {
            calls.incrementAndGet();
            throw new InsufficientFundsException("nope");
        });

        assertThatThrownBy(supplier::get).isInstanceOf(InsufficientFundsException.class);
        assertThat(calls.get()).isEqualTo(1); // ignored by retry
    }

    @Test
    void circuitOpensAfterFailures_andShortCircuits() {
        CircuitBreaker breaker = config.accountCircuitBreaker();
        Supplier<String> failing = CircuitBreaker.decorateSupplier(breaker, () -> {
            throw new AccountException("down");
        });

        for (int i = 0; i < 10; i++) { // minimumNumberOfCalls=10, all fail -> 100% > 50%
            assertThatThrownBy(failing::get).isInstanceOf(AccountException.class);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(failing::get).isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void businessDeclineDoesNotOpenCircuit() {
        CircuitBreaker breaker = config.accountCircuitBreaker();
        Supplier<String> declining = CircuitBreaker.decorateSupplier(breaker, () -> {
            throw new InsufficientFundsException("nope");
        });

        for (int i = 0; i < 15; i++) {
            assertThatThrownBy(declining::get).isInstanceOf(InsufficientFundsException.class);
        }

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
