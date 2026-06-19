package com.example.account.service;

/** The transfer currency does not match both accounts' currency (no FX here). Maps to HTTP 422. */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(String requested, String from, String to) {
        super("Currency mismatch: requested " + requested + ", from " + from + ", to " + to);
    }
}
