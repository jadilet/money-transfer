package com.example.transfer.web;

import com.example.transfer.domain.Transfer;
import com.example.transfer.service.CreateTransferCommand;
import com.example.transfer.service.TransferService;
import com.example.transfer.web.dto.TransferRequest;
import com.example.transfer.web.dto.TransferResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(@Valid @RequestBody TransferRequest request) {
        Transfer transfer = transferService.create(new CreateTransferCommand(
                request.idempotencyKey(),
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                request.currency()
        ));
        return ResponseEntity
                .created(URI.create("/api/transfers/" + transfer.getId()))
                .body(TransferResponse.from(transfer));
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable UUID id) {
        return TransferResponse.from(transferService.getById(id));
    }
}
