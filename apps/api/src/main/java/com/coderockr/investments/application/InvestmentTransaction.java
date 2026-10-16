package com.coderockr.investments.application;

import com.coderockr.investments.domain.Investment;
import com.coderockr.investments.domain.StoredWithdrawal;
import io.smallrye.mutiny.Uni;
import java.util.Optional;
import java.util.UUID;

public interface InvestmentTransaction {
    Uni<Optional<Investment>> findInvestmentForUpdate(UUID id);

    Uni<Optional<StoredWithdrawal>> findWithdrawalByIdempotencyKey(String key);

    Uni<Void> insertWithdrawal(StoredWithdrawal withdrawal);
}
