package com.coderockr.investments.application;

import com.coderockr.investments.domain.InvestmentCalculator;
import com.coderockr.investments.domain.InvestmentRuleViolation;
import com.coderockr.investments.domain.WithdrawalQuote;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class PreviewWithdrawalUseCase {
    private final InvestmentStore store;
    private final InvestmentCalculator calculator;
    private final Clock clock;

    @Inject
    public PreviewWithdrawalUseCase(InvestmentStore store, InvestmentCalculator calculator, Clock clock) {
        this.store = store;
        this.calculator = calculator;
        this.clock = clock;
    }

    public Uni<WithdrawalQuote> execute(UUID id, LocalDate withdrawnOn) {
        LocalDate today = LocalDate.now(clock);
        if (withdrawnOn == null) {
            throw new InvestmentRuleViolation("A data de resgate é obrigatória.");
        }
        return store.find(id)
                .onItem().transform(optional -> optional.orElseThrow(InvestmentNotFound::new))
                .onItem().transformToUni(investment -> Uni.createFrom()
                        .item(() -> calculator.quoteWithdrawal(investment, withdrawnOn, today))
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()));
    }
}
