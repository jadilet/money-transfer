package com.example.account.web.dto;

import java.util.UUID;

/** Confirmation that a transfer was applied (or was already applied, idempotently). */
public record ApplyTransferResponse(UUID transferId, String result) {
}
