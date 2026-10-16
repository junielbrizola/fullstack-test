package com.coderockr.investments.application;

import com.coderockr.investments.domain.Investment;
import com.coderockr.investments.domain.InvestmentCalculator;
import com.coderockr.investments.domain.Money;
import com.coderockr.investments.domain.StoredWithdrawal;
import com.coderockr.investments.domain.WithdrawalQuote;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;

@ApplicationScoped
public class InvestmentPresenter {
    private final InvestmentCalculator calculator;

    @Inject
    public InvestmentPresenter(InvestmentCalculator calculator) {
        this.calculator = calculator;
    }

    public InvestmentView present(Investment investment, LocalDate today) {
        if (investment.withdrawn()) {
            StoredWithdrawal stored = investment.withdrawal().orElseThrow();
            WithdrawalQuote quote = stored.quote();
            return new InvestmentView(
                    investment.id(),
                    investment.ownerName(),
                    investment.createdOn(),
                    investment.initialAmount(),
                    quote.withdrawnOn(),
                    quote.completedPeriods(),
                    quote.grossAmount(),
                    quote.gainAmount(),
                    "WITHDRAWN",
                    quote);
        }

        long periods = calculator.completedPeriods(investment.createdOn(), today);
        Money gross = calculator.grossBalance(investment.initialAmount(), periods);
        Money gain = gross.minus(investment.initialAmount());
        return new InvestmentView(
                investment.id(),
                investment.ownerName(),
                investment.createdOn(),
                investment.initialAmount(),
                today,
                periods,
                gross,
                gain,
                "ACTIVE",
                null);
    }
}
