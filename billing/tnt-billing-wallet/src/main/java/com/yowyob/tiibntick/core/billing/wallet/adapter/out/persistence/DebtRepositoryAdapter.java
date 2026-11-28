package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IDebtRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletDebt;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** {@link IDebtRepository} over {@link DatabaseClient} ({@code billing.wallet_debts}); timestamps in UTC. */
@Component
public class DebtRepositoryAdapter implements IDebtRepository {

    private final DatabaseClient db;

    public DebtRepositoryAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<WalletDebt> findById(UUID id) {
        return db.sql("SELECT * FROM billing.wallet_debts WHERE id = :id").bind("id", id)
                .map(DebtRepositoryAdapter::debt).one();
    }

    @Override
    public Mono<WalletDebt> findByReference(String reference) {
        return db.sql("SELECT * FROM billing.wallet_debts WHERE reference = :r").bind("r", reference)
                .map(DebtRepositoryAdapter::debt).one();
    }

    @Override
    public Mono<WalletDebt> save(WalletDebt d, boolean isNew) {
        DatabaseClient.GenericExecuteSpec spec = isNew
                ? db.sql("INSERT INTO billing.wallet_debts (id, tenant_id, debtor_type, debtor_id, reason, reference,"
                        + " delivery_id, amount, currency, status, debtor_wallet_id, kernel_collection_id, note,"
                        + " created_at, updated_at) VALUES (:id, :tenant, :type, :debtor, :reason, :reference,"
                        + " :delivery, :amount, :currency, :status, :wallet, :collection, :note, :created, :updated)")
                        .bind("tenant", d.tenantId()).bind("type", d.debtorType().name())
                        .bind("reason", d.reason().name()).bind("reference", d.reference())
                        .bind("currency", d.currency())
                        .bind("created", utc(d.createdAt() != null ? d.createdAt() : Instant.now()))
                : db.sql("UPDATE billing.wallet_debts SET debtor_id = :debtor, amount = :amount, status = :status,"
                        + " debtor_wallet_id = :wallet, kernel_collection_id = :collection, note = :note,"
                        + " updated_at = :updated WHERE id = :id");
        spec = spec.bind("id", d.id()).bind("amount", d.amount()).bind("status", d.status().name())
                .bind("updated", utc(d.updatedAt() != null ? d.updatedAt() : Instant.now()));
        spec = bind(spec, "debtor", d.debtorId(), UUID.class);
        spec = bind(spec, "wallet", d.debtorWalletId(), UUID.class);
        spec = bind(spec, "collection", d.kernelCollectionId(), UUID.class);
        spec = bind(spec, "note", d.note() == null || d.note().length() <= 255 ? d.note() : d.note().substring(0, 255),
                String.class);
        if (isNew) {
            spec = bind(spec, "delivery", d.deliveryId(), UUID.class);
        }
        return spec.fetch().rowsUpdated().thenReturn(d);
    }

    @Override
    public Flux<WalletDebt> byDebtor(UUID debtorId) {
        return db.sql("SELECT * FROM billing.wallet_debts WHERE debtor_id = :d ORDER BY created_at DESC")
                .bind("d", debtorId).map(DebtRepositoryAdapter::debt).all();
    }

    @Override
    public Flux<WalletDebt> byStatus(WalletDebt.Status status, int limit) {
        return status == null
                ? db.sql("SELECT * FROM billing.wallet_debts ORDER BY created_at DESC LIMIT :l").bind("l", limit)
                .map(DebtRepositoryAdapter::debt).all()
                : db.sql("SELECT * FROM billing.wallet_debts WHERE status = :s ORDER BY created_at DESC LIMIT :l")
                .bind("s", status.name()).bind("l", limit).map(DebtRepositoryAdapter::debt).all();
    }

    private static WalletDebt debt(Readable r) {
        return new WalletDebt(r.get("id", UUID.class), r.get("tenant_id", UUID.class),
                WalletDebt.DebtorType.valueOf(r.get("debtor_type", String.class)), r.get("debtor_id", UUID.class),
                WalletDebt.Reason.valueOf(r.get("reason", String.class)), r.get("reference", String.class),
                r.get("delivery_id", UUID.class), r.get("amount", BigDecimal.class), r.get("currency", String.class),
                WalletDebt.Status.valueOf(r.get("status", String.class)), r.get("debtor_wallet_id", UUID.class),
                r.get("kernel_collection_id", UUID.class), r.get("note", String.class),
                instant(r.get("created_at", LocalDateTime.class)), instant(r.get("updated_at", LocalDateTime.class)));
    }

    private static <T> DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec, String name,
                                                              T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static LocalDateTime utc(Instant i) {
        return i == null ? null : LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime t) {
        return t == null ? null : t.toInstant(ZoneOffset.UTC);
    }
}
