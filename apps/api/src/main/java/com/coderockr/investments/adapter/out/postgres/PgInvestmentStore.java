package com.coderockr.investments.adapter.out.postgres;

import com.coderockr.investments.application.InvestmentStore;
import com.coderockr.investments.application.InvestmentTransaction;
import com.coderockr.investments.application.PageCursor;
import com.coderockr.investments.domain.Investment;
import com.coderockr.investments.domain.Money;
import com.coderockr.investments.domain.StoredWithdrawal;
import com.coderockr.investments.domain.WithdrawalQuote;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.sqlclient.Pool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@ApplicationScoped
public class PgInvestmentStore implements InvestmentStore {
    private static final String INVESTMENT_COLUMNS = "i.id, i.owner_name, i.created_on, i.initial_amount";
    private static final String WITHDRAWAL_COLUMNS = "w.id AS withdrawal_id, w.investment_id, w.withdrawn_on, "
            + "w.initial_amount AS withdrawal_initial_amount, w.completed_periods, w.gross_amount, "
            + "w.gain_amount, w.tax_rate, w.tax_amount, w.net_amount, w.calculation_version, w.idempotency_key";
    private static final String JOINED_SELECT = "SELECT " + INVESTMENT_COLUMNS + ", " + WITHDRAWAL_COLUMNS
            + " FROM investment i LEFT JOIN withdrawal w ON w.investment_id = i.id ";
    private static final String WITHDRAWAL_SELECT = "SELECT id AS withdrawal_id, investment_id, withdrawn_on, "
            + "initial_amount AS withdrawal_initial_amount, completed_periods, gross_amount, gain_amount, "
            + "tax_rate, tax_amount, net_amount, calculation_version, idempotency_key FROM withdrawal ";

    @Inject
    Pool pool;

    @Override
    public Uni<Investment> create(Investment investment, String idempotencyKey) {
        String sql = "INSERT INTO investment (id, owner_name, created_on, initial_amount, creation_idempotency_key) "
                + "VALUES ($1, $2, $3, $4, $5) ON CONFLICT (creation_idempotency_key) DO NOTHING RETURNING id";
        Tuple parameters = Tuple.tuple()
                .addUUID(investment.id())
                .addString(investment.ownerName())
                .addValue(investment.createdOn())
                .addBigDecimal(investment.initialAmount().value())
                .addString(idempotencyKey);
        return pool.preparedQuery(sql).execute(parameters).flatMap(rows -> {
            if (rows.size() == 1) {
                return Uni.createFrom().item(investment);
            }
            return pool.preparedQuery(JOINED_SELECT + " WHERE i.creation_idempotency_key = $1")
                    .execute(Tuple.of(idempotencyKey))
                    .map(existing -> {
                        if (existing.size() == 0) {
                            throw new IllegalStateException("A chave idempotente de criação não foi encontrada após conflito.");
                        }
                        return mapInvestment(existing.iterator().next());
                    });
        });
    }

    @Override
    public Uni<Optional<Investment>> find(UUID id) {
        String sql = JOINED_SELECT + " WHERE i.id = $1";
        return pool.preparedQuery(sql).execute(Tuple.of(id)).map(rows -> {
            if (rows.size() == 0) {
                return Optional.empty();
            }
            return Optional.of(mapInvestment(rows.iterator().next()));
        });
    }

    @Override
    public Uni<Optional<StoredWithdrawal>> findWithdrawalByIdempotencyKey(String key) {
        return findWithdrawal(pool, WITHDRAWAL_SELECT + " WHERE idempotency_key = $1", Tuple.of(key));
    }

    @Override
    public Uni<List<Investment>> list(
            String ownerName,
            LocalDate createdFrom,
            LocalDate createdTo,
            PageCursor cursor,
            int limitPlusOne) {
        StringBuilder sql = new StringBuilder(JOINED_SELECT);
        Tuple parameters = Tuple.tuple();
        List<String> predicates = new ArrayList<>();
        int parameterIndex = 1;

        if (ownerName != null) {
            predicates.add("i.owner_name = $" + parameterIndex);
            parameters.addString(ownerName);
            parameterIndex++;
        }
        if (createdFrom != null) {
            predicates.add("i.created_on >= $" + parameterIndex + "::date");
            parameters.addValue(createdFrom);
            parameterIndex++;
        }
        if (createdTo != null) {
            predicates.add("i.created_on <= $" + parameterIndex + "::date");
            parameters.addValue(createdTo);
            parameterIndex++;
        }
        if (cursor != null) {
            predicates.add("(i.created_on, i.id) < ($" + parameterIndex + "::date, $"
                    + (parameterIndex + 1) + "::uuid)");
            parameters.addValue(cursor.createdOn());
            parameters.addUUID(cursor.id());
            parameterIndex += 2;
        }

        if (!predicates.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", predicates));
        }
        sql.append(" ORDER BY i.created_on DESC, i.id DESC LIMIT $").append(parameterIndex);
        parameters.addInteger(limitPlusOne);
        return pool.preparedQuery(sql.toString()).execute(parameters).map(PgInvestmentStore::mapInvestments);
    }

    @Override
    public <T> Uni<T> inTransaction(Function<InvestmentTransaction, Uni<T>> work) {
        return pool.withTransaction(connection -> work.apply(new PgInvestmentTransaction(connection)));
    }

    private static List<Investment> mapInvestments(RowSet<Row> rows) {
        List<Investment> investments = new ArrayList<>(rows.size());
        for (Row row : rows) {
            investments.add(mapInvestment(row));
        }
        return List.copyOf(investments);
    }

    private static Investment mapInvestment(Row row) {
        UUID id = row.getUUID("id");
        String ownerName = row.getString("owner_name");
        LocalDate createdOn = (LocalDate) row.getValue("created_on");
        Money initialAmount = Money.of(row.getBigDecimal("initial_amount"));
        Object withdrawalId = row.getValue("withdrawal_id");
        Optional<StoredWithdrawal> withdrawal = withdrawalId == null
                ? Optional.empty()
                : Optional.of(mapWithdrawal(row));
        return new Investment(id, ownerName, createdOn, initialAmount, withdrawal);
    }

    private static StoredWithdrawal mapWithdrawal(Row row) {
        UUID investmentId = row.getUUID("investment_id");
        WithdrawalQuote quote = new WithdrawalQuote(
                investmentId,
                (LocalDate) row.getValue("withdrawn_on"),
                Money.of(row.getBigDecimal("withdrawal_initial_amount")),
                row.getLong("completed_periods"),
                Money.of(row.getBigDecimal("gross_amount")),
                Money.of(row.getBigDecimal("gain_amount")),
                row.getBigDecimal("tax_rate"),
                Money.of(row.getBigDecimal("tax_amount")),
                Money.of(row.getBigDecimal("net_amount")),
                row.getString("calculation_version"));
        return new StoredWithdrawal(row.getUUID("withdrawal_id"), quote, row.getString("idempotency_key"));
    }

    private static Uni<Optional<StoredWithdrawal>> findWithdrawal(Pool pool, String sql, Tuple parameters) {
        return pool.preparedQuery(sql).execute(parameters).map(rows -> rows.size() == 0
                ? Optional.empty()
                : Optional.of(mapWithdrawal(rows.iterator().next())));
    }

    private static Uni<Optional<StoredWithdrawal>> findWithdrawal(
            SqlConnection connection,
            String sql,
            Tuple parameters) {
        return connection.preparedQuery(sql).execute(parameters).map(rows -> rows.size() == 0
                ? Optional.empty()
                : Optional.of(mapWithdrawal(rows.iterator().next())));
    }

    private static final class PgInvestmentTransaction implements InvestmentTransaction {
        private final SqlConnection connection;

        private PgInvestmentTransaction(SqlConnection connection) {
            this.connection = connection;
        }

        @Override
        public Uni<Optional<Investment>> findInvestmentForUpdate(UUID id) {
            String sql = "SELECT id, owner_name, created_on, initial_amount FROM investment WHERE id = $1 FOR UPDATE";
            return connection.preparedQuery(sql).execute(Tuple.of(id)).flatMap(rows -> {
                if (rows.size() == 0) {
                    return Uni.createFrom().item(Optional.empty());
                }
                Row row = rows.iterator().next();
                Investment active = new Investment(
                        row.getUUID("id"),
                        row.getString("owner_name"),
                        (LocalDate) row.getValue("created_on"),
                        Money.of(row.getBigDecimal("initial_amount")),
                        Optional.empty());
                return findWithdrawalByInvestmentId(id)
                        .map(withdrawal -> Optional.of(withdrawal.isPresent()
                                ? new Investment(active.id(), active.ownerName(), active.createdOn(), active.initialAmount(), withdrawal)
                                : active));
            });
        }

        @Override
        public Uni<Optional<StoredWithdrawal>> findWithdrawalByIdempotencyKey(String key) {
            return findWithdrawal(connection, WITHDRAWAL_SELECT + " WHERE idempotency_key = $1", Tuple.of(key));
        }

        private Uni<Optional<StoredWithdrawal>> findWithdrawalByInvestmentId(UUID investmentId) {
            return findWithdrawal(connection, WITHDRAWAL_SELECT + " WHERE investment_id = $1", Tuple.of(investmentId));
        }

        @Override
        public Uni<Void> insertWithdrawal(StoredWithdrawal stored) {
            WithdrawalQuote quote = stored.quote();
            String sql = "INSERT INTO withdrawal (id, investment_id, withdrawn_on, initial_amount, completed_periods, "
                    + "gross_amount, gain_amount, tax_rate, tax_amount, net_amount, calculation_version, idempotency_key) "
                    + "VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)";
            Tuple parameters = Tuple.tuple()
                    .addUUID(stored.id())
                    .addUUID(quote.investmentId())
                    .addValue(quote.withdrawnOn())
                    .addBigDecimal(quote.initialAmount().value())
                    .addLong(quote.completedPeriods())
                    .addBigDecimal(quote.grossAmount().value())
                    .addBigDecimal(quote.gainAmount().value())
                    .addBigDecimal(quote.taxRate())
                    .addBigDecimal(quote.taxAmount().value())
                    .addBigDecimal(quote.netAmount().value())
                    .addString(quote.calculationVersion())
                    .addString(stored.idempotencyKey());
            return connection.preparedQuery(sql).execute(parameters).onItem().ignore().andContinueWithNull();
        }
    }
}
