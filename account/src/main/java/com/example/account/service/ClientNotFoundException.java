package com.example.account.service;

import java.util.UUID;

/** No client exists with the given id. Maps to HTTP 404. */
public class ClientNotFoundException extends RuntimeException {

    public ClientNotFoundException(UUID clientId) {
        super("Client not found: " + clientId);
    }
}
