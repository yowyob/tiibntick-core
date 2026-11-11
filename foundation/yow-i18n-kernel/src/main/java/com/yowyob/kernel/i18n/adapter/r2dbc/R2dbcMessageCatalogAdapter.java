package com.yowyob.kernel.i18n.adapter.r2dbc;

import com.yowyob.kernel.i18n.application.port.out.MessageCatalogPort;
import com.yowyob.kernel.i18n.domain.model.MessageEntry;
import com.yowyob.kernel.i18n.domain.model.MessageVariant;
import com.yowyob.kernel.i18n.domain.model.MessageVersion;
import com.yowyob.kernel.i18n.domain.model.MissingTranslation;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@link MessageCatalogPort} over the {@code i18n.message} / {@code i18n.message_history} tables. */
public class R2dbcMessageCatalogAdapter implements MessageCatalogPort {

    private static final String COLUMNS =
            "id, namespace, msg_key, language, variant, text, draft_text, version, updated_at, updated_by";

    private final DatabaseClient db;

    public R2dbcMessageCatalogAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Flux<MessageEntry> findAllLive() {
        return db.sql("SELECT " + COLUMNS + " FROM i18n.message WHERE text IS NOT NULL")
                .map((row, md) -> toEntry(row))
                .all();
    }

    @Override
    public Mono<String> revision() {
        return db.sql("SELECT count(*)::text || '|' || coalesce(max(updated_at)::text, '') AS rev "
                        + "FROM i18n.message WHERE text IS NOT NULL")
                .map((row, md) -> row.get("rev", String.class))
                .one();
    }

    @Override
    public Flux<MessageEntry> search(String namespace, String keyPrefix, String language, boolean draftsOnly,
                                     int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM i18n.message WHERE 1=1");
        Map<String, Object> binds = new java.util.LinkedHashMap<>();
        if (namespace != null && !namespace.isBlank()) {
            sql.append(" AND namespace = :ns");
            binds.put("ns", namespace);
        }
        if (keyPrefix != null && !keyPrefix.isBlank()) {
            sql.append(" AND msg_key LIKE :prefix ESCAPE '\\'");
            binds.put("prefix", escapeLike(keyPrefix) + "%");
        }
        if (language != null && !language.isBlank()) {
            sql.append(" AND language = :lang");
            binds.put("lang", language);
        }
        if (draftsOnly) {
            sql.append(" AND draft_text IS NOT NULL");
        }
        sql.append(" ORDER BY namespace, msg_key, language, variant LIMIT :limit OFFSET :offset");
        binds.put("limit", Math.max(1, Math.min(limit, 500)));
        binds.put("offset", Math.max(0, offset));
        DatabaseClient.GenericExecuteSpec spec = db.sql(sql.toString());
        for (Map.Entry<String, Object> b : binds.entrySet()) {
            spec = spec.bind(b.getKey(), b.getValue());
        }
        return spec.map((row, md) -> toEntry(row)).all();
    }

    @Override
    public Mono<MessageEntry> find(String namespace, String key, String language, String variant) {
        return db.sql("SELECT " + COLUMNS + " FROM i18n.message "
                        + "WHERE namespace = :ns AND msg_key = :key AND language = :lang AND variant = :variant")
                .bind("ns", namespace).bind("key", key).bind("lang", language)
                .bind("variant", MessageVariant.normalize(variant))
                .map((row, md) -> toEntry(row))
                .one();
    }

    @Override
    public Mono<MessageEntry> save(String namespace, String key, String language, String variant, String text,
                                   boolean publish, String actor) {
        String v = MessageVariant.normalize(variant);
        String sql = publish
                ? "WITH up AS (INSERT INTO i18n.message(id, namespace, msg_key, language, variant, text, version, updated_by) "
                  + "VALUES (:id, :ns, :key, :lang, :variant, :text, 1, :actor) "
                  + "ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET text = EXCLUDED.text, "
                  + "draft_text = NULL, version = i18n.message.version + 1, updated_by = EXCLUDED.updated_by, "
                  + "updated_at = now() RETURNING id, version, text, updated_by) "
                  + "INSERT INTO i18n.message_history(message_id, version, text, published_by) "
                  + "SELECT id, version, text, updated_by FROM up"
                : "INSERT INTO i18n.message(id, namespace, msg_key, language, variant, draft_text, version, updated_by) "
                  + "VALUES (:id, :ns, :key, :lang, :variant, :text, 0, :actor) "
                  + "ON CONFLICT (namespace, msg_key, language, variant) DO UPDATE SET draft_text = EXCLUDED.draft_text, "
                  + "updated_by = EXCLUDED.updated_by, updated_at = now()";
        return db.sql(sql)
                .bind("id", UUID.randomUUID()).bind("ns", namespace).bind("key", key).bind("lang", language)
                .bind("variant", v).bind("text", text).bind("actor", actor == null ? "system" : actor)
                .fetch().rowsUpdated()
                .then(find(namespace, key, language, v));
    }

    @Override
    public Mono<MessageEntry> publishDraft(String namespace, String key, String language, String variant,
                                           String actor) {
        String v = MessageVariant.normalize(variant);
        return db.sql("WITH up AS (UPDATE i18n.message SET text = draft_text, draft_text = NULL, "
                        + "version = version + 1, updated_by = :actor, updated_at = now() "
                        + "WHERE namespace = :ns AND msg_key = :key AND language = :lang AND variant = :variant "
                        + "AND draft_text IS NOT NULL RETURNING id, version, text, updated_by) "
                        + "INSERT INTO i18n.message_history(message_id, version, text, published_by) "
                        + "SELECT id, version, text, updated_by FROM up")
                .bind("ns", namespace).bind("key", key).bind("lang", language).bind("variant", v)
                .bind("actor", actor == null ? "system" : actor)
                .fetch().rowsUpdated()
                .filter(n -> n > 0)
                .flatMap(n -> find(namespace, key, language, v));
    }

    @Override
    public Flux<MessageVersion> history(String namespace, String key, String language, String variant) {
        return db.sql("SELECT h.version, h.text, h.published_at, h.published_by FROM i18n.message_history h "
                        + "JOIN i18n.message m ON m.id = h.message_id "
                        + "WHERE m.namespace = :ns AND m.msg_key = :key AND m.language = :lang AND m.variant = :variant "
                        + "ORDER BY h.version DESC")
                .bind("ns", namespace).bind("key", key).bind("lang", language)
                .bind("variant", MessageVariant.normalize(variant))
                .map((row, md) -> new MessageVersion(
                        row.get("version", Integer.class),
                        row.get("text", String.class),
                        toInstant(row.get("published_at", OffsetDateTime.class)),
                        row.get("published_by", String.class)))
                .all();
    }

    @Override
    public Flux<MissingTranslation> findMissing(String namespace, List<String> languages) {
        if (languages == null || languages.isEmpty()) {
            return Flux.empty();
        }
        boolean scoped = namespace != null && !namespace.isBlank();
        String sql = "SELECT a.namespace, a.msg_key, a.variant, l.language AS missing "
                + "FROM (SELECT DISTINCT namespace, msg_key, variant FROM i18n.message WHERE text IS NOT NULL"
                + (scoped ? " AND namespace = :ns" : "") + ") a "
                + "CROSS JOIN unnest(CAST(:langs AS text[])) AS l(language) "
                + "WHERE NOT EXISTS (SELECT 1 FROM i18n.message m WHERE m.namespace = a.namespace "
                + "AND m.msg_key = a.msg_key AND m.variant = a.variant AND m.language = l.language "
                + "AND m.text IS NOT NULL) ORDER BY a.namespace, a.msg_key, l.language";
        DatabaseClient.GenericExecuteSpec spec = db.sql(sql).bind("langs", languages.toArray(String[]::new));
        if (scoped) {
            spec = spec.bind("ns", namespace);
        }
        return spec.map((row, md) -> new MissingTranslation(
                        row.get("namespace", String.class),
                        row.get("msg_key", String.class),
                        row.get("variant", String.class),
                        row.get("missing", String.class)))
                .all();
    }

    // ── mapping ────────────────────────────────────────────────────────────

    private static MessageEntry toEntry(Row row) {
        return new MessageEntry(
                row.get("id", UUID.class),
                row.get("namespace", String.class),
                row.get("msg_key", String.class),
                row.get("language", String.class),
                row.get("variant", String.class),
                row.get("text", String.class),
                row.get("draft_text", String.class),
                row.get("version", Integer.class),
                toInstant(row.get("updated_at", OffsetDateTime.class)),
                row.get("updated_by", String.class));
    }

    private static java.time.Instant toInstant(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
