package com.coderockr.investments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.coderockr.investments.domain.Investment;
import com.coderockr.investments.domain.InvestmentCalculator;
import com.coderockr.investments.domain.InvestmentConflict;
import com.coderockr.investments.domain.InvestmentRuleViolation;
import com.coderockr.investments.domain.Money;
import com.coderockr.investments.domain.StoredWithdrawal;
import io.smallrye.mutiny.Uni;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateInvestmentUseCaseTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Test
    void validRequestPersistsBeforeReturningView() {
        RecordingInvestmentStore store = new RecordingInvestmentStore();
        CreateInvestmentUseCase useCase = new CreateInvestmentUseCase(
                store,
                new InvestmentPresenter(new InvestmentCalculator()),
                Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));

        InvestmentView view = useCase.execute(
                        "  Pessoa de Teste  ", TODAY.minusMonths(1), "1000.00", "create-0001")
                .await().indefinitely();

        assertNotNull(store.persisted);
        assertEquals("create-0001", store.idempotencyKey);
        assertEquals(view.id(), store.persisted.id());
        assertEquals("Pessoa de Teste", view.ownerName());
        assertEquals(Money.of(new BigDecimal("1000.00")), store.persisted.initialAmount());
    }

    @Test
    void rejectsNulInOwnerAndIdempotencyKeyBeforePersistence() {
        RecordingInvestmentStore store = new RecordingInvestmentStore();
        CreateInvestmentUseCase useCase = new CreateInvestmentUseCase(
                store,
                new InvestmentPresenter(new InvestmentCalculator()),
                Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));
        String nul = String.valueOf((char) 0);

        assertThrows(InvalidRequest.class,
                () -> useCase.execute("Pessoa" + nul + " de Teste", TODAY, "1000.00", "create-0011"));
        assertThrows(InvalidRequest.class,
                () -> useCase.execute("Pessoa de Teste", TODAY, "1000.00", "create-" + nul + "-11"));
        assertEquals(0, store.createCalls);
    }

    @Test
    void sameKeyAndPayloadReplayTheOriginalInvestment() {
        RecordingInvestmentStore store = new RecordingInvestmentStore();
        CreateInvestmentUseCase useCase = new CreateInvestmentUseCase(
                store,
                new InvestmentPresenter(new InvestmentCalculator()),
                Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));

        InvestmentView first = useCase.execute("Pessoa de Teste", TODAY, "1000.00", "create-0002")
                .await().indefinitely();
        InvestmentView replay = useCase.execute("Pessoa de Teste", TODAY, "1000.00", "create-0002")
                .await().indefinitely();

        assertEquals(first.id(), replay.id());
        assertEquals(1, store.persistedByKey.size());
    }

    @Test
    void creationDateBelowFinitePostgresDateRangeFailsBeforePersistence() {
        RecordingInvestmentStore store = new RecordingInvestmentStore();
        CreateInvestmentUseCase useCase = new CreateInvestmentUseCase(
                store,
                new InvestmentPresenter(new InvestmentCalculator()),
                Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));

        assertThrows(InvestmentRuleViolation.class,
                () -> useCase.execute(
                        "Pessoa de Teste",
                        Investment.MIN_SUPPORTED_DATE.minusDays(1),
                        "1000.00",
                        "create-0006").await().indefinitely());
        assertEquals(0, store.createCalls);
    }

    @Test
    void finitePostgresMinimumDateIsAccepted() {
        RecordingInvestmentStore store = new RecordingInvestmentStore();
        CreateInvestmentUseCase useCase = new CreateInvestmentUseCase(
                store,
                new InvestmentPresenter(new InvestmentCalculator()),
                Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));

        useCase.execute(
                        "Pessoa de Teste", Investment.MIN_SUPPORTED_DATE, "0", "create-0007")
                .await().indefinitely();

        assertEquals(Investment.MIN_SUPPORTED_DATE, store.persisted.createdOn());
    }

    @Test
    void sameKeyWithEquivalentNormalizedPayloadReplaysTheOriginalInvestment() {
        RecordingInvestmentStore store = new RecordingInvestmentStore();
        CreateInvestmentUseCase useCase = new CreateInvestmentUseCase(
                store,
                new InvestmentPresenter(new InvestmentCalculator()),
                Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));

        InvestmentView first = useCase.execute(" Ana ", TODAY, "1", "create-0005").await().indefinitely();
        InvestmentView replay = useCase.execute("Ana", TODAY, "1.00", "create-0005").await().indefinitely();

        assertEquals(first.id(), replay.id());
        assertEquals(1, store.persistedByKey.size());
    }

    @Test
    void sameKeyWithDifferentPayloadReturnsConflict() {
        RecordingInvestmentStore store = new RecordingInvestmentStore();
        CreateInvestmentUseCase useCase = new CreateInvestmentUseCase(
                store,
                new InvestmentPresenter(new InvestmentCalculator()),
                Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));
        useCase.execute("Pessoa de Teste", TODAY, "1000.00", "create-0003").await().indefinitely();

        assertThrows(InvestmentConflict.class,
                () -> useCase.execute("Outra Pessoa", TODAY, "1000.00", "create-0003").await().indefinitely());
    }

    @Test
    void sameKeyWithFutureDateFailsValidationBeforeReplayLookup() {
        RecordingInvestmentStore store = new RecordingInvestmentStore();
        CreateInvestmentUseCase useCase = new CreateInvestmentUseCase(
                store,
                new InvestmentPresenter(new InvestmentCalculator()),
                Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));
        useCase.execute("Pessoa de Teste", TODAY, "1000.00", "create-0004").await().indefinitely();

        assertThrows(InvestmentRuleViolation.class,
                () -> useCase.execute("Pessoa de Teste", TODAY.plusDays(1), "1000.00", "create-0004")
                        .await().indefinitely());
        assertThrows(InvestmentRuleViolation.class,
                () -> useCase.execute("Pessoa de Teste", TODAY, "valor-inválido", "create-0004")
                        .await().indefinitely());
        assertEquals(1, store.createCalls);
        assertEquals(1, store.persistedByKey.size());
    }

    private static final class RecordingInvestmentStore implements InvestmentStore {
        private Investment persisted;
        private String idempotencyKey;
        private int createCalls;
        private final Map<String, Investment> persistedByKey = new HashMap<>();

        @Override
        public Uni<Investment> create(Investment investment, String key) {
            createCalls++;
            return Uni.createFrom().item(() -> {
                Investment existing = persistedByKey.get(key);
                if (existing != null) return existing;
                persistedByKey.put(key, investment);
                persisted = investment;
                idempotencyKey = key;
                return persisted;
            });
        }

        @Override
        public Uni<Optional<Investment>> find(UUID id) {
            return Uni.createFrom().item(Optional.empty());
        }

        @Override
        public Uni<Optional<StoredWithdrawal>> findWithdrawalByIdempotencyKey(String key) {
            return Uni.createFrom().item(Optional.empty());
        }

        @Override
        public Uni<List<Investment>> list(
                String ownerName, LocalDate createdFrom, LocalDate createdTo, PageCursor cursor, int limitPlusOne) {
            return Uni.createFrom().item(List.of());
        }

        @Override
        public <T> Uni<T> inTransaction(java.util.function.Function<InvestmentTransaction, Uni<T>> work) {
            throw new UnsupportedOperationException("Transações não são usadas neste cenário.");
        }
    }
}
