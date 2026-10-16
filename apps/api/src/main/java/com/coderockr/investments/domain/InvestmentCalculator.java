package com.coderockr.investments.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public final class InvestmentCalculator {
    public static final String CALCULATION_VERSION = "1";
    public static final BigDecimal TAX_UNDER_ONE_YEAR = new BigDecimal("0.2250");
    public static final BigDecimal TAX_ONE_TO_TWO_YEARS = new BigDecimal("0.1850");
    public static final BigDecimal TAX_OVER_TWO_YEARS = new BigDecimal("0.1500");
    private static final BigInteger MONTHLY_RATE_NUMERATOR = BigInteger.valueOf(13);
    private static final BigInteger MONTHLY_RATE_DENOMINATOR = BigInteger.valueOf(2_500);
    private static final BigInteger HALF_MONTHLY_RATE_DENOMINATOR = BigInteger.valueOf(1_250);

    public long completedPeriods(LocalDate createdOn, LocalDate asOfDate) {
        if (asOfDate.isBefore(createdOn)) {
            return 0;
        }

        long monthDifference = ChronoUnit.MONTHS.between(YearMonth.from(createdOn), YearMonth.from(asOfDate));
        LocalDate anniversary = createdOn.plusMonths(monthDifference);
        if (anniversary.isAfter(asOfDate)) {
            monthDifference--;
        }
        return Math.max(0, monthDifference);
    }

    public Money grossBalance(Money principal, long completedPeriods) {
        if (completedPeriods < 0) {
            throw new IllegalArgumentException("O número de períodos não pode ser negativo.");
        }

        BigInteger balanceCents = principal.value().movePointRight(Money.SCALE).toBigIntegerExact();
        if (balanceCents.signum() == 0) {
            return Money.ZERO;
        }

        for (long period = 0; period < completedPeriods; period++) {
            BigInteger[] quotientAndRemainder = balanceCents.multiply(MONTHLY_RATE_NUMERATOR)
                    .divideAndRemainder(MONTHLY_RATE_DENOMINATOR);
            BigInteger gainCents = quotientAndRemainder[0];
            if (quotientAndRemainder[1].compareTo(HALF_MONTHLY_RATE_DENOMINATOR) >= 0) {
                gainCents = gainCents.add(BigInteger.ONE);
            }
            balanceCents = balanceCents.add(gainCents);
        }
        return Money.of(new BigDecimal(balanceCents, Money.SCALE));
    }

    public WithdrawalQuote quoteWithdrawal(Investment investment, LocalDate withdrawnOn, LocalDate today) {
        if (investment.withdrawn()) {
            throw new InvestmentConflict("O investimento já foi resgatado.");
        }
        if (withdrawnOn.isBefore(investment.createdOn())) {
            throw new InvestmentRuleViolation("A data de resgate não pode anteceder a criação do investimento.");
        }
        if (withdrawnOn.isAfter(today)) {
            throw new InvestmentRuleViolation("A data de resgate não pode estar no futuro.");
        }

        long periods = completedPeriods(investment.createdOn(), withdrawnOn);
        Money gross = grossBalance(investment.initialAmount(), periods);
        Money gain = gross.minus(investment.initialAmount());
        BigDecimal taxRate = taxRate(investment.createdOn(), withdrawnOn);
        Money tax = Money.of(gain.value().multiply(taxRate).setScale(Money.SCALE, RoundingMode.HALF_UP));
        Money net = gross.minus(tax);

        return new WithdrawalQuote(
                investment.id(),
                withdrawnOn,
                investment.initialAmount(),
                periods,
                gross,
                gain,
                taxRate,
                tax,
                net,
                CALCULATION_VERSION);
    }

    public BigDecimal taxRate(LocalDate createdOn, LocalDate withdrawnOn) {
        LocalDate firstAnniversary = createdOn.plusYears(1);
        LocalDate secondAnniversary = createdOn.plusYears(2);
        if (withdrawnOn.isBefore(firstAnniversary)) {
            return TAX_UNDER_ONE_YEAR;
        }
        if (!withdrawnOn.isAfter(secondAnniversary)) {
            return TAX_ONE_TO_TWO_YEARS;
        }
        return TAX_OVER_TWO_YEARS;
    }
}
