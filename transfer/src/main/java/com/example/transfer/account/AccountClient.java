package com.example.transfer.account;

/**
 * Boundary to the account service, which owns the authoritative balances. The implementation
 * makes a synchronous, idempotent call that moves money atomically in the account service's
 * own database. This is the transfer saga's critical path: the money has really moved only
 * once this returns normally.
 */
public interface AccountClient {

    /**
     * Atomically debit the source and credit the destination in the account service.
     *
     * @throws InsufficientFundsException if the source account lacks funds (business decline)
     * @throws AccountException           if the account service rejects the move or is unreachable
     */
    void applyTransfer(ApplyTransferCommand command);
}
