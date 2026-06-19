package com.example.transfer.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.transfer.resilience.ResilienceConfig;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Verifies the account REST client's HTTP mapping and resilience: business declines are not retried, transient 5xx are. */
class RestClientAccountClientTest {

    private static final String BASE_URL = "http://account-svc";
    private final ResilienceConfig resilience = new ResilienceConfig();

    private ApplyTransferCommand command() {
        return new ApplyTransferCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100"), "KGS", "key-1");
    }

    private record Fixture(RestClientAccountClient client, MockRestServiceServer server) {
    }

    private Fixture newFixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientAccountClient client = new RestClientAccountClient(
                builder, BASE_URL, resilience.accountRetry(), resilience.accountCircuitBreaker());
        return new Fixture(client, server);
    }

    @Test
    void success_postsOnce() {
        Fixture f = newFixture();
        f.server().expect(ExpectedCount.once(), requestTo(BASE_URL + "/internal/transfers"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        assertThatCode(() -> f.client().applyTransfer(command())).doesNotThrowAnyException();
        f.server().verify();
    }

    @Test
    void decline_422_isTerminal_andSurfacesTheRealReason() {
        Fixture f = newFixture();
        f.server().expect(ExpectedCount.once(), requestTo(BASE_URL + "/internal/transfers"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Account aaaa has insufficient funds\",\"code\":\"INSUFFICIENT_FUNDS\"}"));

        assertThatThrownBy(() -> f.client().applyTransfer(command()))
                .isInstanceOf(AccountDeclinedException.class)
                .hasMessageContaining("insufficient funds");
        f.server().verify(); // exactly one request, no retry
    }

    @Test
    void accountNotFound_422_reportsNotFound_notInsufficientFunds() {
        Fixture f = newFixture();
        f.server().expect(ExpectedCount.once(), requestTo(BASE_URL + "/internal/transfers"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Account not found: aaaa\",\"code\":\"ACCOUNT_NOT_FOUND\"}"));

        assertThatThrownBy(() -> f.client().applyTransfer(command()))
                .isInstanceOf(AccountDeclinedException.class)
                .hasMessageContaining("Account not found");
        f.server().verify();
    }

    @Test
    void serverError_503_isRetriedThenFails() {
        Fixture f = newFixture();
        f.server().expect(ExpectedCount.times(3), requestTo(BASE_URL + "/internal/transfers"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> f.client().applyTransfer(command()))
                .isInstanceOf(AccountException.class);
        f.server().verify(); // three attempts (maxAttempts)
    }
}
