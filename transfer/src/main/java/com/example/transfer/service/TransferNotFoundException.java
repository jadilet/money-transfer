package com.example.transfer.service;

import java.util.UUID;

/** Thrown when a transfer lookup finds nothing. Maps to HTTP 404 in the web layer. */
public class TransferNotFoundException extends RuntimeException {

    public TransferNotFoundException(UUID id) {
        super("Transfer not found: " + id);
    }
}
