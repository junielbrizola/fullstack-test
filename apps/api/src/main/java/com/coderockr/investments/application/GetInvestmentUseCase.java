package com.coderockr.investments.application;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class GetInvestmentUseCase {
    private final InvestmentStore store;
    private final InvestmentPresenter presenter;
    private final Clock clock;

    @Inject
    public GetInvestmentUseCase(InvestmentStore store, InvestmentPresenter presenter, Clock clock) {
        this.store = store;
        this.presenter = presenter;
        this.clock = clock;
    }

    public Uni<InvestmentView> execute(UUID id) {
        return store.find(id)
                .onItem().transform(optional -> optional.orElseThrow(InvestmentNotFound::new))
                .onItem().transformToUni(investment -> Uni.createFrom()
                        .item(() -> presenter.present(investment, LocalDate.now(clock)))
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()));
    }
}
