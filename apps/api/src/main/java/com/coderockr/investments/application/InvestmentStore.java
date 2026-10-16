package com.coderockr.investments.application;

import com.coderockr.investments.domain.Investment;
import com.coderockr.investments.domain.StoredWithdrawal;
import io.smallrye.mutiny.Uni;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface InvestmentStore {
    Uni<Investment> create(Investment investment, String idempotencyKey);

    Uni<Optional<Investment>> find(UUID id);

    Uni<Optional<StoredWithdrawal>> findWithdrawalByIdempotencyKey(String key);

    Uni<List<Investment>> list(
            String ownerName,
            LocalDate createdFrom,
            LocalDate createdTo,
            PageCursor cursor,
            int limitPlusOne);

    <T> Uni<T> inTransaction(Function<InvestmentTransaction, Uni<T>> work);
}
