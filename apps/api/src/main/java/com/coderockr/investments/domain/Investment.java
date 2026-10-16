package com.coderockr.investments.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Investment(
        UUID id,
        String ownerName,
        LocalDate createdOn,
        Money initialAmount,
        Optional<StoredWithdrawal> withdrawal) {

    // PostgreSQL's finite DATE bounds, represented with Java's proleptic ISO year numbering.
    public static final LocalDate MIN_SUPPORTED_DATE = LocalDate.of(-4_713, 11, 24);
    public static final LocalDate MAX_SUPPORTED_DATE = LocalDate.of(5_874_897, 12, 31);

    public Investment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(createdOn, "createdOn");
        Objects.requireNonNull(initialAmount, "initialAmount");
        if (createdOn.isBefore(MIN_SUPPORTED_DATE) || createdOn.isAfter(MAX_SUPPORTED_DATE)) {
            throw new IllegalArgumentException("A data do investimento precisa ser finita no intervalo PostgreSQL DATE.");
        }
        if (initialAmount.value().compareTo(Money.MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("O principal excede o limite NUMERIC(19,2).");
        }
        withdrawal = withdrawal == null ? Optional.empty() : withdrawal;
    }

    public boolean withdrawn() {
        return withdrawal.isPresent();
    }
}
