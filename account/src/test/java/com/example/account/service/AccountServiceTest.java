package com.example.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.account.domain.Account;
import com.example.account.domain.AccountNotActiveException;
import com.example.account.domain.AccountRepository;
import com.example.account.domain.Client;
import com.example.account.domain.ClientRepository;
import com.example.account.domain.InsufficientFundsException;
import com.example.account.domain.ProcessedTransferRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

@SpringBootTest
class AccountServiceTest {

    @Autowired
    private AccountService service;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private ClientRepository clients;
    @Autowired
    private ProcessedTransferRepository processed;
    @Autowired
    private CacheManager cacheManager;

    private UUID clientA;
    private UUID clientB;
    private Account from;
    private Account to;

    @BeforeEach
    void setUp() {
        processed.deleteAll();
        accounts.deleteAll();
        clients.deleteAll();
        cacheManager.getCache(AccountServiceImpl.ACCOUNTS_BY_CLIENT_CACHE).clear();

        clientA = clients.save(Client.of("Adilet", "adilet@example.com")).getId();
        clientB = clients.save(Client.of("Brother", "brother@example.com")).getId();
        from = accounts.save(Account.open(clientA, "KGS", new BigDecimal("1000.00")));
        to = accounts.save(Account.open(clientB, "KGS", new BigDecimal("5000.00")));
    }

    private ApplyTransferCommand transfer(UUID id, String amount, String currency) {
        return new ApplyTransferCommand(id, from.getId(), to.getId(), new BigDecimal(amount), currency, "key-" + id);
    }

    @Test
    void appliesTransfer_movesMoneyAtomically() {
        service.applyTransfer(transfer(UUID.randomUUID(), "100", "KGS"));

        assertThat(accounts.findById(from.getId()).orElseThrow().getBalance()).isEqualByComparingTo("900.00");
        assertThat(accounts.findById(to.getId()).orElseThrow().getBalance()).isEqualByComparingTo("5100.00");
        assertThat(processed.count()).isEqualTo(1);
    }

    @Test
    void insufficientFunds_isDeclined_andNothingMoves() {
        assertThatThrownBy(() -> service.applyTransfer(transfer(UUID.randomUUID(), "2000", "KGS")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(accounts.findById(from.getId()).orElseThrow().getBalance()).isEqualByComparingTo("1000.00");
        assertThat(accounts.findById(to.getId()).orElseThrow().getBalance()).isEqualByComparingTo("5000.00");
        assertThat(processed.count()).isZero();
    }

    @Test
    void replayWithSameTransferId_appliesOnce() {
        UUID id = UUID.randomUUID();
        service.applyTransfer(transfer(id, "100", "KGS"));
        service.applyTransfer(transfer(id, "100", "KGS")); // replay — must be a no-op

        assertThat(accounts.findById(from.getId()).orElseThrow().getBalance()).isEqualByComparingTo("900.00");
        assertThat(processed.count()).isEqualTo(1);
    }

    @Test
    void currencyMismatch_isDeclined() {
        assertThatThrownBy(() -> service.applyTransfer(transfer(UUID.randomUUID(), "100", "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void unknownAccount_isDeclined() {
        ApplyTransferCommand cmd = new ApplyTransferCommand(
                UUID.randomUUID(), from.getId(), UUID.randomUUID(), new BigDecimal("100"), "KGS", "k");
        assertThatThrownBy(() -> service.applyTransfer(cmd)).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void frozenAccount_isDeclined() {
        from.freeze();
        accounts.save(from);

        assertThatThrownBy(() -> service.applyTransfer(transfer(UUID.randomUUID(), "100", "KGS")))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void listAccounts_returnsAndCaches() {
        var list = service.listAccounts(clientA);
        assertThat(list).hasSize(1);
        // second call should be served from the cache (entry present)
        assertThat(cacheManager.getCache(AccountServiceImpl.ACCOUNTS_BY_CLIENT_CACHE).get(clientA)).isNotNull();
    }

    @Test
    void transfer_evictsCachedAccountLists() {
        service.listAccounts(clientA); // populate cache
        assertThat(cacheManager.getCache(AccountServiceImpl.ACCOUNTS_BY_CLIENT_CACHE).get(clientA)).isNotNull();

        service.applyTransfer(transfer(UUID.randomUUID(), "100", "KGS"));

        assertThat(cacheManager.getCache(AccountServiceImpl.ACCOUNTS_BY_CLIENT_CACHE).get(clientA)).isNull();
    }

    @Test
    void listAccounts_unknownClient_throws() {
        assertThatThrownBy(() -> service.listAccounts(UUID.randomUUID()))
                .isInstanceOf(ClientNotFoundException.class);
    }
}
