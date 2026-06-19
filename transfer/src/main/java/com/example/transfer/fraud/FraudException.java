package com.example.transfer.fraud;

/** Raised when the fraud service is unreachable or errors. The caller decides fail-open vs fail-closed. */
public class FraudException extends RuntimeException {

    public FraudException(String message) {
        super(message);
    }

    public FraudException(String message, Throwable cause) {
        super(message, cause);
    }
}
