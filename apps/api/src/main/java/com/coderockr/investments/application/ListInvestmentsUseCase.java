package com.coderockr.investments.application;

import com.coderockr.investments.domain.Investment;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ListInvestmentsUseCase {
    private static final int MAX_LIMIT = 100;

    private final InvestmentStore store;
    private final InvestmentPresenter presenter;
    private final Clock clock;

    @Inject
    public ListInvestmentsUseCase(InvestmentStore store, InvestmentPresenter presenter, Clock clock) {
        this.store = store;
        this.presenter = presenter;
        this.clock = clock;
    }

    public Uni<InvestmentPage> execute(
            String ownerName,
            LocalDate createdFrom,
            LocalDate createdTo,
            int limit,
            String rawCursor) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequest("O limite da página deve estar entre 1 e 100.");
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new InvalidRequest("A data inicial não pode ser posterior à data final.");
        }
        String normalizedOwner = ownerName == null || ownerName.isBlank() ? null : ownerName.strip();
        if (normalizedOwner != null && normalizedOwner.indexOf('\u0000') >= 0) {
            throw new InvalidRequest("O filtro de titular contém um caractere não permitido.");
        }
        if (normalizedOwner != null && normalizedOwner.codePointCount(0, normalizedOwner.length()) > 100) {
            throw new InvalidRequest("O filtro de titular deve ter no máximo 100 caracteres.");
        }
        PageCursor cursor = PageCursor.decode(rawCursor);
        LocalDate today = LocalDate.now(clock);

        return store.list(normalizedOwner, createdFrom, createdTo, cursor, limit + 1)
                .onItem().transformToUni(rows -> Uni.createFrom()
                        .item(() -> toPage(rows, limit, today))
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()));
    }

    private InvestmentPage toPage(List<Investment> rows, int limit, LocalDate today) {
        boolean hasMore = rows.size() > limit;
        List<Investment> visible = hasMore ? rows.subList(0, limit) : rows;
        List<InvestmentView> views = new ArrayList<>(visible.size());
        for (Investment investment : visible) {
            views.add(presenter.present(investment, today));
        }
        String nextCursor = null;
        if (hasMore && !visible.isEmpty()) {
            Investment last = visible.getLast();
            nextCursor = new PageCursor(last.createdOn(), last.id()).encode();
        }
        return new InvestmentPage(List.copyOf(views), nextCursor, limit);
    }
}
