package com.example.account.service;

import com.example.account.domain.Account;
import com.example.account.domain.AccountRepository;
import com.example.account.domain.ClientRepository;
import com.example.account.domain.ProcessedTransfer;
import com.example.account.domain.ProcessedTransferRepository;
import com.example.account.domain.TransferResult;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {

    static final String ACCOUNTS_BY_CLIENT_CACHE = "accountsByClient";

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);

    private final AccountRepository accounts;
    private final ProcessedTransferRepository processed;
    private final ClientRepository clients;
    private final CacheManager cacheManager;

    public AccountServiceImpl(AccountRepository accounts,
                              ProcessedTransferRepository processed,
                              ClientRepository clients,
                              CacheManager cacheManager) {
        this.accounts = accounts;
        this.processed = processed;
        this.clients = clients;
        this.cacheManager = cacheManager;
    }

    @Override
    @Transactional
    public void applyTransfer(ApplyTransferCommand command) {
        // Idempotency: a transfer we've already applied is a no-op (don't move money twice).
        if (processed.existsById(command.transferId())) {
            log.debug("Transfer {} already applied; no-op", command.transferId());
            return;
        }

        String currency = command.currency().toUpperCase(Locale.ROOT);

        // Lock both accounts in a deterministic (sorted) order so concurrent A->B and B->A transfers
        // can't deadlock. Then map the locked rows back to source/destination.
        boolean fromIsFirst = command.fromAccountId().compareTo(command.toAccountId()) <= 0;
        UUID firstId = fromIsFirst ? command.fromAccountId() : command.toAccountId();
        UUID secondId = fromIsFirst ? command.toAccountId() : command.fromAccountId();

        Account first = accounts.findWithLockById(firstId)
                .orElseThrow(() -> new AccountNotFoundException(firstId));
        Account second = accounts.findWithLockById(secondId)
                .orElseThrow(() -> new AccountNotFoundException(secondId));

        Account from = fromIsFirst ? first : second;
        Account to = fromIsFirst ? second : first;

        if (!from.getCurrency().equals(currency) || !to.getCurrency().equals(currency)) {
            throw new CurrencyMismatchException(currency, from.getCurrency(), to.getCurrency());
        }

        from.debit(command.amount());   // throws InsufficientFundsException / AccountNotActiveException -> rolls back
        to.credit(command.amount());
        accounts.save(from);
        accounts.save(to);
        processed.save(ProcessedTransfer.of(command.transferId(), TransferResult.APPLIED, command.amount()));

        // Balances changed, so the cached account lists for both owners are now stale.
        evictAccountList(from.getClientId());
        evictAccountList(to.getClientId());

        log.info("Applied transfer {}: {} -> {} amount={} {}",
                command.transferId(), from.getId(), to.getId(), command.amount(), currency);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = ACCOUNTS_BY_CLIENT_CACHE, key = "#clientId")
    public List<AccountView> listAccounts(UUID clientId) {
        if (!clients.existsById(clientId)) {
            throw new ClientNotFoundException(clientId);
        }
        return accounts.findByClientId(clientId).stream().map(AccountView::from).toList();
    }

    private void evictAccountList(UUID clientId) {
        Cache cache = cacheManager.getCache(ACCOUNTS_BY_CLIENT_CACHE);
        if (cache != null) {
            cache.evict(clientId);
        }
    }
}
