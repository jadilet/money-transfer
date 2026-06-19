package com.example.transfer.fraud;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Placeholder fraud client used until the real fraud service exists. Approves everything, except —
 * for local testing — it rejects amounts strictly above {@code fraud.stub.reject-above} when that
 * property is set. Active by default; set {@code fraud.client=rest} to switch to the real HTTP client.
 */
@Component
@ConditionalOnProperty(name = "fraud.client", havingValue = "stub", matchIfMissing = true)
public class StubFraudClient implements FraudClient {

    private static final Logger log = LoggerFactory.getLogger(StubFraudClient.class);

    private final BigDecimal rejectAbove;

    public StubFraudClient(@Value("${fraud.stub.reject-above:#{null}}") BigDecimal rejectAbove) {
        this.rejectAbove = rejectAbove;
    }

    @Override
    public FraudDecision check(FraudCheckCommand command) {
        if (rejectAbove != null && command.amount().compareTo(rejectAbove) > 0) {
            log.info("[stub fraud] rejecting transferId={} amount={} > {}",
                    command.transferId(), command.amount(), rejectAbove);
            return FraudDecision.reject("amount exceeds stub threshold " + rejectAbove);
        }
        log.info("[stub fraud] approving transferId={} {} -> {} amount={} {}",
                command.transferId(), command.fromAccountId(), command.toAccountId(),
                command.amount(), command.currency());
        return FraudDecision.approve();
    }
}
