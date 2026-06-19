package com.example.account.domain;

/** Recorded outcome of applying a transfer, so a replay returns the same answer. */
public enum TransferResult {
    APPLIED,
    REJECTED
}
