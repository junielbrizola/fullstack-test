package com.coderockr.investments.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal value) implements Comparable<Money> {
    public static final int SCALE = 2;
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");
    public static final Money ZERO = new Money(BigDecimal.ZERO.setScale(SCALE));

    public Money {
        Objects.requireNonNull(value, "value");
        if (value.scale() > SCALE) {
            throw new IllegalArgumentException("O valor aceita no máximo duas casas decimais.");
        }
        BigDecimal normalized = value.setScale(SCALE, RoundingMode.UNNECESSARY);
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException("O valor não pode ser negativo.");
        }
        value = normalized;
    }

    public static Money of(BigDecimal value) {
        return new Money(value);
    }

    public static Money parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("O valor é obrigatório.");
        }
        try {
            return of(new BigDecimal(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("O valor deve ser um decimal válido.", exception);
        }
    }

    public Money plus(Money other) {
        return of(value.add(other.value));
    }

    public Money minus(Money other) {
        BigDecimal result = value.subtract(other.value);
        if (result.signum() < 0) {
            throw new IllegalArgumentException("O resultado monetário não pode ser negativo.");
        }
        return of(result);
    }

    @Override
    public int compareTo(Money other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
