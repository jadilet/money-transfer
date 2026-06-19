package com.example.transfer.service;

/** Thrown when a transfer request breaks a business rule (e.g. same source and destination). Maps to HTTP 400. */
public class InvalidTransferException extends RuntimeException {

    public InvalidTransferException(String message) {
        super(message);
    }
}
