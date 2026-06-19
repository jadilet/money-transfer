package com.example.transfer.account;

/**
 * The account service refused the money-move for a business reason (insufficient funds, account not
 * found, currency mismatch, not active). Terminal — retrying won't change the outcome — so the
 * transfer fails with this reason rather than being retried. Carries the account's actual reason.
 */
public class AccountDeclinedException extends AccountException {

    public AccountDeclinedException(String reason) {
        super(reason);
    }
}
