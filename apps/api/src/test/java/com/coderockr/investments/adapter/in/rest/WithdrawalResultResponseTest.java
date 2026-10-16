package com.coderockr.investments.adapter.in.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.coderockr.investments.domain.Money;
import com.coderockr.investments.domain.WithdrawalQuote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WithdrawalResultResponseTest {
    @Test
    void emitsOnlyTheTaxRateStringsDeclaredByTheApiContract() {
        assertEquals("0.225", responseRate("0.2250"));
        assertEquals("0.185", responseRate("0.1850"));
        assertEquals("0.15", responseRate("0.1500"));
    }

    private String responseRate(String rate) {
        WithdrawalQuote quote = new WithdrawalQuote(
                UUID.fromString("68b4b7a8-cd36-4f4e-bbf0-c86bcdf9d703"),
                LocalDate.of(2026, 10, 16),
                Money.ZERO,
                0,
                Money.ZERO,
                Money.ZERO,
                new BigDecimal(rate),
                Money.ZERO,
                Money.ZERO,
                "1");
        return WithdrawalResultResponse.from(quote).taxRate();
    }
}
