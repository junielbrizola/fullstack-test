package com.coderockr.investments.application;

import com.coderockr.investments.domain.Money;
import com.coderockr.investments.domain.WithdrawalQuote;
import java.time.LocalDate;
import java.util.UUID;

public record InvestmentView(
        UUID id,
        String ownerName,
        LocalDate createdOn,
        Money initialAmount,
        LocalDate asOfDate,
        long completedPeriods,
        Money grossBalance,
        Money gain,
        String status,
        WithdrawalQuote withdrawal) {
}
