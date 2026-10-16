package com.coderockr.investments.domain;

import java.util.Objects;
import java.util.UUID;

public record StoredWithdrawal(UUID id, WithdrawalQuote quote, String idempotencyKey) {
    public StoredWithdrawal {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }
}
