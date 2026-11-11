package com.yowyob.tiibntick.core.i18n.adapter.out.persistence;

import com.yowyob.tiibntick.core.i18n.application.port.out.UserLanguagePreferenceRepository;
import com.yowyob.tiibntick.core.i18n.domain.model.UserLanguagePreference;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

public class R2dbcUserLanguagePreferenceRepository implements UserLanguagePreferenceRepository {

    private static final String SELECT = "SELECT user_id, language, source, updated_at FROM i18n.user_language_preference";

    private final DatabaseClient db;

    public R2dbcUserLanguagePreferenceRepository(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<UserLanguagePreference> findByUserId(UUID userId) {
        return db.sql(SELECT + " WHERE user_id = :id").bind("id", userId)
                .map((row, md) -> map(row)).one();
    }

    @Override
    public Mono<UserLanguagePreference> upsert(UUID userId, String language, String source) {
        return db.sql("INSERT INTO i18n.user_language_preference(user_id, language, source) VALUES (:id, :lang, :src) "
                        + "ON CONFLICT (user_id) DO UPDATE SET language = EXCLUDED.language, source = EXCLUDED.source, "
                        + "updated_at = now()")
                .bind("id", userId).bind("lang", language).bind("src", source)
                .fetch().rowsUpdated()
                .then(findByUserId(userId));
    }

    @Override
    public Mono<UserLanguagePreference> insertIfAbsent(UUID userId, String language, String source) {
        return db.sql("INSERT INTO i18n.user_language_preference(user_id, language, source) VALUES (:id, :lang, :src) "
                        + "ON CONFLICT (user_id) DO NOTHING")
                .bind("id", userId).bind("lang", language).bind("src", source)
                .fetch().rowsUpdated()
                .then(findByUserId(userId));
    }

    private static UserLanguagePreference map(io.r2dbc.spi.Row row) {
        OffsetDateTime t = row.get("updated_at", OffsetDateTime.class);
        return new UserLanguagePreference(row.get("user_id", UUID.class), row.get("language", String.class),
                row.get("source", String.class), t == null ? null : t.toInstant());
    }
}
