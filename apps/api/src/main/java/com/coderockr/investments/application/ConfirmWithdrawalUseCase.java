package com.coderockr.investments.application;

import com.coderockr.investments.domain.Investment;
import com.coderockr.investments.domain.InvestmentCalculator;
import com.coderockr.investments.domain.InvestmentConflict;
import com.coderockr.investments.domain.InvestmentRuleViolation;
import com.coderockr.investments.domain.StoredWithdrawal;
import com.coderockr.investments.domain.WithdrawalQuote;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.pgclient.PgException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@ApplicationScoped
public class ConfirmWithdrawalUseCase {
    private final InvestmentStore store;
    private final InvestmentCalculator calculator;
    private final Clock clock;

    @Inject
    public ConfirmWithdrawalUseCase(InvestmentStore store, InvestmentCalculator calculator, Clock clock) {
        this.store = store;
        this.calculator = calculator;
        this.clock = clock;
    }

    public Uni<WithdrawalQuote> execute(UUID investmentId, LocalDate withdrawnOn, String idempotencyKey) {
        validateKey(idempotencyKey);
        if (withdrawnOn == null) {
            throw new InvestmentRuleViolation("A data de resgate é obrigatória.");
        }

        LocalDate today = LocalDate.now(clock);
        return store.findWithdrawalByIdempotencyKey(idempotencyKey)
                .flatMap(existing -> {
                    if (existing.isPresent()) {
                        return Uni.createFrom().item(
                                replayOrConflict(existing.orElseThrow(), investmentId, withdrawnOn));
                    }
                    return store.find(investmentId)
                            .onItem().transform(optional -> optional.orElseThrow(InvestmentNotFound::new))
                            .flatMap(snapshot -> {
                                if (snapshot.withdrawn()) {
                                    throw new InvestmentConflict(
                                            "O investimento já foi resgatado com outra chave idempotente.");
                                }
                                return Uni.createFrom()
                                        .item(() -> calculator.quoteWithdrawal(snapshot, withdrawnOn, today))
                                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                                        .flatMap(quote -> store.inTransaction(transaction ->
                                                transaction.findWithdrawalByIdempotencyKey(idempotencyKey)
                                                        .flatMap(existingInTransaction -> {
                                                            if (existingInTransaction.isPresent()) {
                                                                return Uni.createFrom().item(replayOrConflict(
                                                                        existingInTransaction.orElseThrow(),
                                                                        investmentId,
                                                                        withdrawnOn));
                                                            }
                                                            return createWithdrawal(
                                                                    transaction,
                                                                    snapshot,
                                                                    quote,
                                                                    withdrawnOn,
                                                                    idempotencyKey);
                                                        })));
                            });
                })
                .onFailure(PgException.class).transform(this::translateUniqueViolation);
    }

    private Uni<WithdrawalQuote> createWithdrawal(
            InvestmentTransaction transaction,
            Investment snapshot,
            WithdrawalQuote quote,
            LocalDate withdrawnOn,
            String idempotencyKey) {
        return transaction.findInvestmentForUpdate(snapshot.id())
                .onItem().transform(optional -> optional.orElseThrow(InvestmentNotFound::new))
                .flatMap(investment -> transaction.findWithdrawalByIdempotencyKey(idempotencyKey)
                        .flatMap(recheckedByKey -> {
                            if (recheckedByKey.isPresent()) {
                                return Uni.createFrom().item(replayOrConflict(
                                        recheckedByKey.orElseThrow(), snapshot.id(), withdrawnOn));
                            }
                            if (investment.withdrawn()) {
                                throw new InvestmentConflict(
                                        "O investimento já foi resgatado com outra chave idempotente.");
                            }
                            if (!sameCalculationInputs(snapshot, investment)) {
                                throw new InvestmentConflict(
                                        "Os dados financeiros do investimento mudaram durante o resgate; tente novamente.");
                            }
                            StoredWithdrawal stored = new StoredWithdrawal(UUID.randomUUID(), quote, idempotencyKey);
                            return transaction.insertWithdrawal(stored).replaceWith(quote);
                        }));
    }

    private boolean sameCalculationInputs(Investment first, Investment second) {
        return first.createdOn().equals(second.createdOn())
                && first.initialAmount().equals(second.initialAmount());
    }

    private WithdrawalQuote replayOrConflict(StoredWithdrawal stored, UUID investmentId, LocalDate withdrawnOn) {
        WithdrawalQuote quote = stored.quote();
        if (!quote.investmentId().equals(investmentId) || !quote.withdrawnOn().equals(withdrawnOn)) {
            throw new InvestmentConflict("A chave idempotente já foi usada em outra operação.");
        }
        return quote;
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()
                || key.codePointCount(0, key.length()) < 8
                || key.codePointCount(0, key.length()) > 100) {
            throw new InvalidRequest("Idempotency-Key deve conter entre 8 e 100 caracteres.");
        }
        if (key.indexOf('\u0000') >= 0) {
            throw new InvalidRequest("Idempotency-Key contém um caractere não permitido.");
        }
    }

    private RuntimeException translateUniqueViolation(PgException exception) {
        if ("23505".equals(exception.getSqlState())) {
            return new InvestmentConflict("O investimento ou a chave idempotente já foi utilizado em um resgate.");
        }
        return exception;
    }
}
