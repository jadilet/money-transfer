package com.example.account.web;

import com.example.account.service.AccountService;
import com.example.account.service.ApplyTransferCommand;
import com.example.account.web.dto.ApplyTransferRequest;
import com.example.account.web.dto.ApplyTransferResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal (service-to-service) endpoint the transfer service calls to move money. Returns 200 when
 * applied (or already applied); business declines surface as 422 via the exception handler.
 */
@RestController
@RequestMapping("/internal/transfers")
public class InternalTransferController {

    private final AccountService accountService;

    public InternalTransferController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ApplyTransferResponse apply(@Valid @RequestBody ApplyTransferRequest request) {
        accountService.applyTransfer(new ApplyTransferCommand(
                request.transferId(),
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                request.currency(),
                request.idempotencyKey()));
        return new ApplyTransferResponse(request.transferId(), "APPLIED");
    }
}
