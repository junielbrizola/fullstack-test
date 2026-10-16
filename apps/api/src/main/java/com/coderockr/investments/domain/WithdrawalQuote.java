package com.coderockr.investments.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WithdrawalQuote(
        UUID investmentId,
        LocalDate withdrawnOn,
        Money initialAmount,
        long completedPeriods,
        Money grossAmount,
        Money gainAmount,
        BigDecimal taxRate,
        Money taxAmount,
        Money netAmount,
        String calculationVersion) {
}
