package com.example.ledger.web;

import com.example.ledger.service.LedgerService;
import com.example.ledger.service.LedgerViews;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read API over the ledger: derived balances (for reconciliation) and per-account statements. */
@RestController
@RequestMapping("/api/ledger-accounts/{accountRef}")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/balance")
    public LedgerViews.Balance balance(@PathVariable UUID accountRef) {
        return ledgerService.balanceOf(accountRef);
    }

    @GetMapping("/postings")
    public List<LedgerViews.PostingLine> postings(@PathVariable UUID accountRef) {
        return ledgerService.postingsOf(accountRef);
    }
}
