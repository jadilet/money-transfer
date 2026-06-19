package com.example.account.web;

import com.example.account.service.AccountService;
import com.example.account.service.AccountView;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public read API for accounts. */
@RestController
@RequestMapping("/api/clients/{clientId}/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountView> list(@PathVariable UUID clientId) {
        return accountService.listAccounts(clientId);
    }
}
