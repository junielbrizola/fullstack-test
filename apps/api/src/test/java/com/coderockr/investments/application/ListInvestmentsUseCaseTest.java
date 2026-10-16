package com.coderockr.investments.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.coderockr.investments.domain.Investment;
import com.coderockr.investments.domain.InvestmentCalculator;
import com.coderockr.investments.domain.Money;
import com.coderockr.investments.domain.StoredWithdrawal;
import io.smallrye.mutiny.Uni;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListInvestmentsUseCaseTest {
    private static final LocalDate CREATED_ON = LocalDate.of(2025, 10, 16);
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void nextCursorUsesLastVisibleItemAndDoesNotSkipLookAheadItem() {
        RecordingInvestmentStore store = new RecordingInvestmentStore(List.of(
                investment(THIRD), investment(SECOND), investment(FIRST)));
        ListInvestmentsUseCase useCase = new ListInvestmentsUseCase(
                store,
                new InvestmentPresenter(new InvestmentCalculator()),
                Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));

        InvestmentPage firstPage = useCase.execute(null, null, null, 2, null).await().indefinitely();
        InvestmentPage secondPage = useCase.execute(null, null, null, 2, firstPage.nextCursor()).await().indefinitely();

        assertEquals(List.of(THIRD, SECOND), firstPage.items().stream().map(InvestmentView::id).toList());
        assertEquals(new PageCursor(CREATED_ON, SECOND), PageCursor.decode(firstPage.nextCursor()));
        assertEquals(List.of(FIRST), secondPage.items().stream().map(InvestmentView::id).toList());
        assertNull(secondPage.nextCursor());
        assertEquals(new PageCursor(CREATED_ON, SECOND), store.lastCursor);
    }

    @Test
    void rejectsNulInOwnerFilterBeforeStoreLookup() {
        RecordingInvestmentStore store = new RecordingInvestmentStore(List.of(investment(THIRD)));
        String ownerName = "Pessoa" + String.valueOf((char) 0) + " Teste";

        assertThrows(InvalidRequest.class, () -> useCase(store).execute(ownerName, null, null, 20, null));

        assertEquals(0, store.listCalls);
    }

    @Test
    void cursorAcceptsFinitePostgresDateBoundsAndRejectsOutOfRangeDates() {
        assertEquals(LocalDate.parse("-4713-11-24"), Investment.MIN_SUPPORTED_DATE);
        assertEquals(LocalDate.parse("+5874897-12-31"), Investment.MAX_SUPPORTED_DATE);
        String id = SECOND.toString();
        String belowMinimum = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((Investment.MIN_SUPPORTED_DATE.minusDays(1) + ":" + id).getBytes(StandardCharsets.UTF_8));
        String aboveMaximum = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((Investment.MAX_SUPPORTED_DATE.plusDays(1) + ":" + id).getBytes(StandardCharsets.UTF_8));
        PageCursor minimum = new PageCursor(Investment.MIN_SUPPORTED_DATE, SECOND);
        PageCursor maximum = new PageCursor(Investment.MAX_SUPPORTED_DATE, SECOND);

        assertThrows(InvalidRequest.class, () -> PageCursor.decode(belowMinimum));
        assertThrows(InvalidRequest.class, () -> PageCursor.decode(aboveMaximum));
        assertEquals(minimum, PageCursor.decode(minimum.encode()));
        assertEquals(maximum, PageCursor.decode(maximum.encode()));
    }

    private static Investment investment(UUID id) {
        return new Investment(id, "Pessoa de Teste", CREATED_ON,
                Money.of(new BigDecimal("1000.00")), Optional.empty());
    }

    private static ListInvestmentsUseCase useCase(RecordingInvestmentStore store) {
        return new ListInvestmentsUseCase(
                store,
                new InvestmentPresenter(new InvestmentCalculator()),
                Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneId.of("America/Sao_Paulo")));
    }

    private static final class RecordingInvestmentStore implements InvestmentStore {
        private final List<Investment> allInvestments;
        private PageCursor lastCursor;
        private int listCalls;

        private RecordingInvestmentStore(List<Investment> investments) {
            allInvestments = investments.stream()
                    .sorted(Comparator.comparing(Investment::createdOn).reversed()
                            .thenComparing(Investment::id, Comparator.reverseOrder()))
                    .toList();
        }

        @Override
        public Uni<Investment> create(Investment investment, String idempotencyKey) {
            return Uni.createFrom().item(investment);
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
            listCalls++;
            lastCursor = cursor;
            List<Investment> eligible = new ArrayList<>();
            for (Investment investment : allInvestments) {
                if (cursor == null || investment.createdOn().isBefore(cursor.createdOn())
                        || (investment.createdOn().equals(cursor.createdOn())
                                && investment.id().compareTo(cursor.id()) < 0)) {
                    eligible.add(investment);
                }
            }
            return Uni.createFrom().item(eligible.stream().limit(limitPlusOne).toList());
        }

        @Override
        public <T> Uni<T> inTransaction(java.util.function.Function<InvestmentTransaction, Uni<T>> work) {
            throw new UnsupportedOperationException("Transações não são usadas neste cenário.");
        }
    }
}
