package com.coderockr.investments.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvestmentCalculatorTest {
    private final InvestmentCalculator calculator = new InvestmentCalculator();

    @Test
    void countsAnchoredMonthlyPeriodsAcrossShortMonths() {
        LocalDate createdOn = LocalDate.of(2025, 1, 31);

        assertEquals(0, calculator.completedPeriods(createdOn, LocalDate.of(2025, 2, 27)));
        assertEquals(1, calculator.completedPeriods(createdOn, LocalDate.of(2025, 2, 28)));
        assertEquals(1, calculator.completedPeriods(createdOn, LocalDate.of(2025, 3, 30)));
        assertEquals(2, calculator.completedPeriods(createdOn, LocalDate.of(2025, 3, 31)));
    }

    @Test
    void compoundsAndRoundsEachMonthlyCreditToCents() {
        Money firstMonth = calculator.grossBalance(Money.of(new BigDecimal("1000.00")), 1);
        Money secondMonth = calculator.grossBalance(Money.of(new BigDecimal("1000.00")), 2);

        assertEquals(new BigDecimal("1005.20"), firstMonth.value());
        assertEquals(new BigDecimal("1010.43"), secondMonth.value());
    }

    @Test
    void roundsMonthlyInterestToTheNearestCent() {
        assertEquals(new BigDecimal("0.96"), calculator.grossBalance(Money.of(new BigDecimal("0.96")), 1).value());
        assertEquals(new BigDecimal("0.98"), calculator.grossBalance(Money.of(new BigDecimal("0.97")), 1).value());
    }

    @Test
    void usesTheSpecifiedTaxBandsAtCalendarAnniversaries() {
        LocalDate createdOn = LocalDate.of(2024, 2, 29);

        assertEquals(new BigDecimal("0.2250"), calculator.taxRate(createdOn, LocalDate.of(2025, 2, 27)));
        assertEquals(new BigDecimal("0.1850"), calculator.taxRate(createdOn, LocalDate.of(2025, 2, 28)));
        assertEquals(new BigDecimal("0.1850"), calculator.taxRate(createdOn, LocalDate.of(2026, 2, 28)));
        assertEquals(new BigDecimal("0.1500"), calculator.taxRate(createdOn, LocalDate.of(2026, 3, 1)));
    }

    @Test
    void doesNotCreateGainForZeroPrincipal() {
        assertEquals(Money.ZERO, calculator.grossBalance(Money.ZERO, 12_000));
    }

    @Test
    void allowsCalculatedBalancesAboveTheInitialPrincipalNumericRange() {
        LocalDate createdOn = LocalDate.of(2025, 1, 31);
        Investment validInvestment = new Investment(
                UUID.fromString("fb7e0ca2-95c1-46b1-868e-5bb58b1a72cd"),
                "Pessoa de Teste",
                createdOn,
                Money.of(Money.MAX_AMOUNT),
                Optional.empty());

        assertEquals(
                new BigDecimal("100519999999999999.99"),
                calculator.quoteWithdrawal(validInvestment, LocalDate.of(2025, 2, 28), LocalDate.of(2025, 2, 28))
                        .grossAmount().value());
    }

    @Test
    void quotesOnlyTheGainAndPersistsTheEffectiveDateInTheResult() {
        Investment investment = new Investment(
                UUID.fromString("2fd03e12-7f08-4cc3-9256-3037b4e37315"),
                "Pessoa de Teste",
                LocalDate.of(2025, 1, 10),
                Money.of(new BigDecimal("1000.00")),
                Optional.empty());

        WithdrawalQuote quote = calculator.quoteWithdrawal(
                investment,
                LocalDate.of(2025, 2, 10),
                LocalDate.of(2026, 1, 1));

        assertEquals(1, quote.completedPeriods());
        assertEquals(new BigDecimal("1005.20"), quote.grossAmount().value());
        assertEquals(new BigDecimal("5.20"), quote.gainAmount().value());
        assertEquals(new BigDecimal("1.17"), quote.taxAmount().value());
        assertEquals(new BigDecimal("1004.03"), quote.netAmount().value());
        assertEquals(LocalDate.of(2025, 2, 10), quote.withdrawnOn());
    }
}
