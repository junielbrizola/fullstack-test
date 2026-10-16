package com.coderockr.investments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.coderockr.investments.domain.Investment;
import com.coderockr.investments.domain.InvestmentCalculator;
import com.coderockr.investments.domain.InvestmentConflict;
import com.coderockr.investments.domain.Money;
import com.coderockr.investments.domain.StoredWithdrawal;
import com.coderockr.investments.domain.WithdrawalQuote;
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

class ConfirmWithdrawalUseCaseTest {
    private static final UUID INVESTMENT_ID = UUID.fromString("68b4b7a8-cd36-4f4e-bbf0-c86bcdf9d703");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);

    @Test
    void retriesWithTheSameKeyAndPayloadReturnThePersistedResult() {
        InMemoryInvestmentStore store = new InMemoryInvestmentStore(investment());
        ConfirmWithdrawalUseCase useCase = useCase(store);
        String key = "withdraw-0001";
        LocalDate withdrawnOn = LocalDate.of(2026, 10, 16);

        WithdrawalQuote first = useCase.execute(INVESTMENT_ID, withdrawnOn, key).await().indefinitely();
        WithdrawalQuote replay = useCase.execute(INVESTMENT_ID, withdrawnOn, key).await().indefinitely();

        assertEquals(first, replay);
        assertEquals(1, store.savedWithdrawals.size());
    }

    @Test
    void rejectsNulInIdempotencyKeyBeforeStoreLookup() {
        InMemoryInvestmentStore store = new InMemoryInvestmentStore(investment());
        String key = "withdraw-" + String.valueOf((char) 0) + "-05";

        assertThrows(InvalidRequest.class,
                () -> useCase(store).execute(INVESTMENT_ID, TODAY, key));

        assertEquals(0, store.withdrawalLookups);
    }

    @Test
    void rechecksTheKeyAfterWaitingForTheInvestmentLock() {
        InMemoryInvestmentStore store = new InMemoryInvestmentStore(investment());
        String key = "withdraw-race-01";
        WithdrawalQuote committedByConcurrentRequest = new InvestmentCalculator().quoteWithdrawal(
                investment(), TODAY, TODAY);
        store.seedOnSecondKeyLookup = new StoredWithdrawal(UUID.randomUUID(), committedByConcurrentRequest, key);

        WithdrawalQuote replay = useCase(store).execute(INVESTMENT_ID, TODAY, key).await().indefinitely();

        assertEquals(committedByConcurrentRequest, replay);
        assertEquals(1, store.savedWithdrawals.size());
    }

    @Test
    void rejectsReusingAKeyForAnotherPayloadOrInvestment() {
        InMemoryInvestmentStore store = new InMemoryInvestmentStore(investment());
        ConfirmWithdrawalUseCase useCase = useCase(store);
        useCase.execute(INVESTMENT_ID, TODAY, "withdraw-0002").await().indefinitely();

        assertThrows(InvestmentConflict.class,
                () -> useCase.execute(INVESTMENT_ID, TODAY.minusDays(1), "withdraw-0002").await().indefinitely());
        assertThrows(InvestmentConflict.class,
                () -> useCase.execute(UUID.randomUUID(), TODAY, "withdraw-0002").await().indefinitely());
        assertEquals(1, store.savedWithdrawals.size());
    }

    @Test
    void rejectsASecondKeyAfterTheInvestmentHasBeenWithdrawn() {
        InMemoryInvestmentStore store = new InMemoryInvestmentStore(investment());
        ConfirmWithdrawalUseCase useCase = useCase(store);
        useCase.execute(INVESTMENT_ID, TODAY, "withdraw-0003").await().indefinitely();

        assertThrows(InvestmentConflict.class,
                () -> useCase.execute(INVESTMENT_ID, TODAY, "withdraw-0004").await().indefinitely());
        assertEquals(1, store.savedWithdrawals.size());
    }

    private static ConfirmWithdrawalUseCase useCase(InMemoryInvestmentStore store) {
        Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        return new ConfirmWithdrawalUseCase(store, new InvestmentCalculator(), clock);
    }

    private static Investment investment() {
        return new Investment(
                INVESTMENT_ID,
                "Pessoa de Teste",
                LocalDate.of(2025, 10, 16),
                Money.of(new BigDecimal("1000.00")),
                Optional.empty());
    }

    private static final class InMemoryInvestmentStore implements InvestmentStore {
        private final Investment investment;
        private final Map<UUID, StoredWithdrawal> savedWithdrawals = new HashMap<>();
        private final Map<String, StoredWithdrawal> withdrawalsByKey = new HashMap<>();
        private StoredWithdrawal seedOnSecondKeyLookup;
        private int idempotencyKeyLookups;
        private int withdrawalLookups;

        private InMemoryInvestmentStore(Investment investment) {
            this.investment = investment;
        }

        @Override
        public Uni<Investment> create(Investment newInvestment, String idempotencyKey) {
            return Uni.createFrom().item(newInvestment);
        }

        @Override
        public Uni<Optional<Investment>> find(UUID id) {
            return Uni.createFrom().item(id.equals(investment.id()) ? Optional.of(currentInvestment()) : Optional.empty());
        }

        @Override
        public Uni<Optional<StoredWithdrawal>> findWithdrawalByIdempotencyKey(String key) {
            withdrawalLookups++;
            return Uni.createFrom().item(Optional.ofNullable(withdrawalsByKey.get(key)));
        }

        @Override
        public Uni<List<Investment>> list(
                String ownerName, LocalDate createdFrom, LocalDate createdTo, PageCursor cursor, int limitPlusOne) {
            return Uni.createFrom().item(List.of(currentInvestment()));
        }

        @Override
        public <T> Uni<T> inTransaction(java.util.function.Function<InvestmentTransaction, Uni<T>> work) {
            return work.apply(new TestTransaction());
        }

        private Investment currentInvestment() {
            StoredWithdrawal withdrawal = savedWithdrawals.get(investment.id());
            return new Investment(
                    investment.id(),
                    investment.ownerName(),
                    investment.createdOn(),
                    investment.initialAmount(),
                    Optional.ofNullable(withdrawal));
        }

        private final class TestTransaction implements InvestmentTransaction {
            @Override
            public Uni<Optional<Investment>> findInvestmentForUpdate(UUID id) {
                return find(id);
            }

            @Override
            public Uni<Optional<StoredWithdrawal>> findWithdrawalByIdempotencyKey(String key) {
                idempotencyKeyLookups++;
                if (idempotencyKeyLookups == 2 && seedOnSecondKeyLookup != null) {
                    save(seedOnSecondKeyLookup);
                }
                return Uni.createFrom().item(Optional.ofNullable(withdrawalsByKey.get(key)));
            }

            @Override
            public Uni<Void> insertWithdrawal(StoredWithdrawal withdrawal) {
                if (savedWithdrawals.containsKey(withdrawal.quote().investmentId())
                        || withdrawalsByKey.containsKey(withdrawal.idempotencyKey())) {
                    return Uni.createFrom().failure(new InvestmentConflict("Resgate duplicado."));
                }
                save(withdrawal);
                return Uni.createFrom().voidItem();
            }

            private void save(StoredWithdrawal withdrawal) {
                savedWithdrawals.put(withdrawal.quote().investmentId(), withdrawal);
                withdrawalsByKey.put(withdrawal.idempotencyKey(), withdrawal);
            }
        }
    }
}
