package com.example.transfer.account;

/** A specific terminal decline: the source account lacks the funds. */
public class InsufficientFundsException extends AccountDeclinedException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
