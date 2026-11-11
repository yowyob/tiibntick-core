package com.yowyob.tiibntick.core.i18n.application.port.out;

import com.yowyob.tiibntick.core.i18n.domain.model.UserLanguagePreference;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserLanguagePreferenceRepository {

    Mono<UserLanguagePreference> findByUserId(UUID userId);

    /** Inserts or replaces the account's language. */
    Mono<UserLanguagePreference> upsert(UUID userId, String language, String source);

    /** Inserts only when the account has none yet; returns the stored preference either way. */
    Mono<UserLanguagePreference> insertIfAbsent(UUID userId, String language, String source);
}
