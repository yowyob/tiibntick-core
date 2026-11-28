package com.yowyob.tiibntick.core.accounting.adapter.out.persistence.kernel;

import com.yowyob.tiibntick.core.accounting.application.port.out.KernelBookkeepingLinkRepository;
import com.yowyob.tiibntick.core.accounting.domain.model.kernel.KernelEntryLink;
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

/** {@link KernelBookkeepingLinkRepository} on {@code accounting.kernel_*_links} (changeset {@code evt-l6-001}). */
@Component
public class KernelBookkeepingLinkRepositoryAdapter implements KernelBookkeepingLinkRepository {

    private static final String ENTRY_COLUMNS = "id, source_key, source_type, source_event_id, owner_type, owner_id,"
            + " reference, amount, currency, fact, kernel_entry_id, status, reason, posted_at, created_at, updated_at";

    private final DatabaseClient db;

    public KernelBookkeepingLinkRepositoryAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<UUID> accountId(String ownerType, UUID ownerId, String code) {
        return link("kernel_account_links", "kernel_account_id", ownerType, ownerId, code);
    }

    @Override
    public Mono<UUID> saveAccount(String ownerType, UUID ownerId, String code, UUID kernelAccountId) {
        return saveLink("kernel_account_links", "kernel_account_id", ownerType, ownerId, code, kernelAccountId);
    }

    @Override
    public Mono<UUID> journalId(String ownerType, UUID ownerId, String code) {
        return link("kernel_journal_links", "kernel_journal_id", ownerType, ownerId, code);
    }

    @Override
    public Mono<UUID> saveJournal(String ownerType, UUID ownerId, String code, UUID kernelJournalId) {
        return saveLink("kernel_journal_links", "kernel_journal_id", ownerType, ownerId, code, kernelJournalId);
    }

    private Mono<UUID> link(String table, String column, String ownerType, UUID ownerId, String code) {
        return db.sql("SELECT " + column + " FROM accounting." + table
                        + " WHERE owner_type = :t AND owner_id = :o AND code = :c")
                .bind("t", ownerType).bind("o", ownerId).bind("c", code)
                .map(r -> r.get(column, UUID.class)).one();
    }

    private Mono<UUID> saveLink(String table, String column, String ownerType, UUID ownerId, String code, UUID id) {
        return db.sql("INSERT INTO accounting." + table + " (id, owner_type, owner_id, code, " + column
                        + ") VALUES (:id, :t, :o, :c, :k) ON CONFLICT (owner_type, owner_id, code) DO NOTHING")
                .bind("id", UUID.randomUUID()).bind("t", ownerType).bind("o", ownerId).bind("c", code).bind("k", id)
                .then()
                .then(link(table, column, ownerType, ownerId, code));
    }

    @Override
    public Mono<KernelEntryLink> findBySourceKey(String sourceKey) {
        return db.sql("SELECT " + ENTRY_COLUMNS + " FROM accounting.kernel_entry_links WHERE source_key = :k")
                .bind("k", sourceKey).map(KernelBookkeepingLinkRepositoryAdapter::entry).one();
    }

    @Override
    public Mono<KernelEntryLink> findById(UUID id) {
        return db.sql("SELECT " + ENTRY_COLUMNS + " FROM accounting.kernel_entry_links WHERE id = :id")
                .bind("id", id).map(KernelBookkeepingLinkRepositoryAdapter::entry).one();
    }

    @Override
    public Mono<KernelEntryLink> save(KernelEntryLink l, boolean isNew) {
        String sql = isNew
                ? "INSERT INTO accounting.kernel_entry_links (" + ENTRY_COLUMNS + ") VALUES (:id, :sourceKey,"
                + " :sourceType, :sourceEventId, :ownerType, :ownerId, :reference, :amount, :currency, :fact,"
                + " :kernelEntryId, :status, :reason, :postedAt, :createdAt, :updatedAt)"
                : "UPDATE accounting.kernel_entry_links SET owner_id = :ownerId, kernel_entry_id = :kernelEntryId,"
                + " status = :status, reason = :reason, posted_at = :postedAt, updated_at = :updatedAt"
                + " WHERE id = :id";
        DatabaseClient.GenericExecuteSpec spec = db.sql(sql).bind("id", l.id()).bind("status", l.status().name());
        spec = nullable(spec, "ownerId", l.ownerId(), UUID.class);
        spec = nullable(spec, "kernelEntryId", l.kernelEntryId(), UUID.class);
        spec = l.reason() != null ? spec.bind("reason", l.reason()) : spec.bindNull("reason", String.class);
        spec = l.postedAt() != null ? spec.bind("postedAt", utc(l.postedAt()))
                : spec.bindNull("postedAt", LocalDateTime.class);
        spec = spec.bind("updatedAt", utc(l.updatedAt()));
        if (isNew) {
            spec = spec.bind("sourceKey", l.sourceKey()).bind("sourceType", l.sourceType())
                    .bind("fact", l.fact()).bind("createdAt", utc(l.createdAt()));
            spec = nullable(spec, "sourceEventId", l.sourceEventId(), String.class);
            spec = nullable(spec, "ownerType", l.ownerType(), String.class);
            spec = nullable(spec, "reference", l.reference(), String.class);
            spec = nullable(spec, "amount", l.amount(), BigDecimal.class);
            spec = nullable(spec, "currency", l.currency(), String.class);
        }
        return spec.then().thenReturn(l);
    }

    @Override
    public Flux<KernelEntryLink> byStatus(KernelEntryLink.Status status, int limit) {
        return (status == null
                ? db.sql("SELECT " + ENTRY_COLUMNS + " FROM accounting.kernel_entry_links ORDER BY created_at DESC"
                + " LIMIT :n")
                : db.sql("SELECT " + ENTRY_COLUMNS + " FROM accounting.kernel_entry_links WHERE status = :s"
                + " ORDER BY created_at DESC LIMIT :n").bind("s", status.name()))
                .bind("n", limit)
                .map(KernelBookkeepingLinkRepositoryAdapter::entry).all();
    }

    private static <T> DatabaseClient.GenericExecuteSpec nullable(DatabaseClient.GenericExecuteSpec spec,
                                                                  String name, T value, Class<T> type) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, type);
    }

    private static LocalDateTime utc(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime t) {
        return t == null ? null : t.toInstant(ZoneOffset.UTC);
    }

    private static KernelEntryLink entry(Readable r) {
        return new KernelEntryLink(r.get("id", UUID.class), r.get("source_key", String.class),
                r.get("source_type", String.class), r.get("source_event_id", String.class),
                r.get("owner_type", String.class), r.get("owner_id", UUID.class), r.get("reference", String.class),
                r.get("amount", BigDecimal.class), r.get("currency", String.class), r.get("fact", String.class),
                r.get("kernel_entry_id", UUID.class), KernelEntryLink.Status.valueOf(r.get("status", String.class)),
                r.get("reason", String.class), instant(r.get("posted_at", LocalDateTime.class)),
                instant(r.get("created_at", LocalDateTime.class)), instant(r.get("updated_at", LocalDateTime.class)));
    }
}
