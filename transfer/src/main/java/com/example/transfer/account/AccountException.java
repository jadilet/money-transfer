package com.example.transfer.account;

/** Raised when the account service cannot complete the money-move (e.g. account not found, or unreachable). */
public class AccountException extends RuntimeException {

    public AccountException(String message) {
        super(message);
    }

    public AccountException(String message, Throwable cause) {
        super(message, cause);
    }
}
