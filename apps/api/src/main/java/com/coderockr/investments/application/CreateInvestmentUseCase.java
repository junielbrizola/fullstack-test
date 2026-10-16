package com.coderockr.investments.application;

import com.coderockr.investments.domain.Investment;
import com.coderockr.investments.domain.InvestmentConflict;
import com.coderockr.investments.domain.InvestmentRuleViolation;
import com.coderockr.investments.domain.Money;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CreateInvestmentUseCase {
    private final InvestmentStore store;
    private final InvestmentPresenter presenter;
    private final Clock clock;

    @Inject
    public CreateInvestmentUseCase(InvestmentStore store, InvestmentPresenter presenter, Clock clock) {
        this.store = store;
        this.presenter = presenter;
        this.clock = clock;
    }

    public Uni<InvestmentView> execute(
            String ownerName,
            LocalDate createdOn,
            String initialAmount,
            String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        if (ownerName == null || ownerName.isBlank()) {
            throw new InvalidRequest("O titular é obrigatório.");
        }
        if (ownerName.indexOf('\u0000') >= 0) {
            throw new InvalidRequest("O titular contém um caractere não permitido.");
        }
        if (ownerName.codePointCount(0, ownerName.length()) > 100) {
            throw new InvalidRequest("O titular deve ter no máximo 100 caracteres.");
        }
        String normalizedOwner = ownerName.strip();
        if (createdOn == null) {
            throw new InvestmentRuleViolation("A data de criação é obrigatória.");
        }
        if (createdOn.isBefore(Investment.MIN_SUPPORTED_DATE) || createdOn.isAfter(Investment.MAX_SUPPORTED_DATE)) {
            throw new InvestmentRuleViolation("A data de criação está fora do intervalo finito DATE do PostgreSQL.");
        }
        LocalDate today = LocalDate.now(clock);
        if (createdOn.isAfter(today)) {
            throw new InvestmentRuleViolation("A data de criação não pode estar no futuro.");
        }

        final Money principal;
        try {
            principal = Money.parse(initialAmount);
        } catch (IllegalArgumentException exception) {
            throw new InvestmentRuleViolation(exception.getMessage());
        }
        if (principal.value().compareTo(Money.MAX_AMOUNT) > 0) {
            throw new InvestmentRuleViolation("O principal excede o limite NUMERIC(19,2).");
        }

        Investment investment = new Investment(UUID.randomUUID(), normalizedOwner, createdOn, principal, Optional.empty());
        return store.create(investment, idempotencyKey)
                .onItem().transformToUni(persisted -> {
                    if (!sameCreationRequest(investment, persisted)) {
                        throw new InvestmentConflict("A chave idempotente já foi usada em outra criação.");
                    }
                    return Uni.createFrom().item(() -> presenter.present(persisted, today))
                            .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
                });
    }

    private boolean sameCreationRequest(Investment requested, Investment persisted) {
        return requested.ownerName().equals(persisted.ownerName())
                && requested.createdOn().equals(persisted.createdOn())
                && requested.initialAmount().equals(persisted.initialAmount());
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()
                || key.codePointCount(0, key.length()) < 8
                || key.codePointCount(0, key.length()) > 100) {
            throw new InvalidRequest("Idempotency-Key deve conter entre 8 e 100 caracteres.");
        }
        if (key.indexOf('\u0000') >= 0) {
            throw new InvalidRequest("Idempotency-Key contém um caractere não permitido.");
        }
    }
}
