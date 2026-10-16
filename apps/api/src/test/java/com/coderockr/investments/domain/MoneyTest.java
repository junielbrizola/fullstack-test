package com.coderockr.investments.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MoneyTest {
    @Test
    void rejectsMissingValuesAsInputErrors() {
        assertThrows(IllegalArgumentException.class, () -> Money.parse(null));
    }

    @Test
    void normalizesWholeAndOneDecimalPlaceValuesToCents() {
        assertEquals("10.00", Money.parse("10").toString());
        assertEquals("10.50", Money.parse("10.5").toString());
    }

    @Test
    void rejectsNegativeAndOverPreciseValues() {
        assertThrows(IllegalArgumentException.class, () -> Money.parse("-1.00"));
        assertThrows(IllegalArgumentException.class, () -> Money.parse("1.001"));
    }
}
