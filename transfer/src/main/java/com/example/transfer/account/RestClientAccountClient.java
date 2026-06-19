package com.example.transfer.account;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * Real HTTP client to the account service, wrapped in resilience policies. The circuit breaker is
 * the outer layer so it records one outcome per call (after retries) and short-circuits when open;
 * the retry is inner, re-attempting transient failures with backoff. Insufficient-funds is a
 * business decline — the policies neither retry it nor count it against the breaker.
 *
 * <p>This is the only account client — the transfer service always calls the real account service.
 */
@Component
public class RestClientAccountClient implements AccountClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientAccountClient.class);

    private final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public RestClientAccountClient(RestClient.Builder builder,
                                   @Value("${account.base-url}") String baseUrl,
                                   @Qualifier("accountRetry") Retry retry,
                                   @Qualifier("accountCircuitBreaker") CircuitBreaker circuitBreaker) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
        log.info("Using REST account client at {}", baseUrl);
    }

    @Override
    public void applyTransfer(ApplyTransferCommand command) {
        Runnable resilient = CircuitBreaker.decorateRunnable(circuitBreaker,
                Retry.decorateRunnable(retry, () -> doApply(command)));
        try {
            resilient.run();
        } catch (CallNotPermittedException e) {
            throw new AccountException("account service unavailable (circuit open)", e);
        }
    }

    private static final Pattern DETAIL = Pattern.compile("\"detail\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private void doApply(ApplyTransferCommand command) {
        try {
            restClient.post()
                    .uri("/internal/transfers")
                    .body(command)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            // 4xx: a terminal business decline. Surface the account's real reason; do not retry.
            throw new AccountDeclinedException(declineReason(e));
        } catch (HttpServerErrorException e) {
            // 5xx: transient server error — retryable.
            throw new AccountException("account server error: " + e.getStatusCode(), e);
        } catch (RuntimeException e) {
            // IO / connection refused / timeout — transient, retryable.
            throw new AccountException("account call failed: " + e.getMessage(), e);
        }
    }

    /** Pull the human-readable reason out of the account's ProblemDetail body. */
    private String declineReason(HttpClientErrorException e) {
        Matcher matcher = DETAIL.matcher(e.getResponseBodyAsString());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "account declined (" + e.getStatusCode().value() + ")";
    }
}
