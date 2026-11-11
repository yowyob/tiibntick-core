package com.yowyob.tiibntick.core.i18n.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yowyob.kernel.i18n.domain.vo.LanguageTags;
import com.yowyob.tiibntick.core.i18n.application.port.in.ManageUserLanguageUseCase;
import com.yowyob.tiibntick.core.i18n.application.port.in.ResolveRecipientLanguageUseCase;
import com.yowyob.tiibntick.core.i18n.application.port.out.UserLanguagePreferenceRepository;
import com.yowyob.tiibntick.core.i18n.domain.model.UserLanguagePreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public class UserLanguageService implements ManageUserLanguageUseCase, ResolveRecipientLanguageUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserLanguageService.class);

    private final UserLanguagePreferenceRepository repository;
    /** userId -> language ("" = none stored). Short TTL: another instance may change it. */
    private final Cache<UUID, String> cache;

    public UserLanguageService(UserLanguagePreferenceRepository repository, Duration cacheTtl) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder().maximumSize(50_000).expireAfterWrite(cacheTtl).build();
    }

    // ── ResolveRecipientLanguageUseCase ────────────────────────────────────

    @Override
    public Mono<String> languageOf(String recipientId, String hint) {
        String fallback = LanguageTags.isSupported(LanguageTags.toPublicCode(hint))
                ? LanguageTags.toPublicCode(hint) : LanguageTags.DEFAULT;
        UUID userId = parse(recipientId);
        if (userId == null) {
            return Mono.just(fallback);
        }
        return stored(userId)
                .map(opt -> opt.orElse(fallback))
                .onErrorResume(e -> {
                    log.warn("Language lookup failed for {} ({}); using {}", userId, e.getMessage(), fallback);
                    return Mono.just(fallback);
                });
    }

    private Mono<Optional<String>> stored(UUID userId) {
        String cached = cache.getIfPresent(userId);
        if (cached != null) {
            return Mono.just(cached.isEmpty() ? Optional.empty() : Optional.of(cached));
        }
        return repository.findByUserId(userId)
                .map(p -> Optional.of(p.language()))
                .defaultIfEmpty(Optional.empty())
                .doOnNext(o -> cache.put(userId, o.orElse("")));
    }

    // ── ManageUserLanguageUseCase ──────────────────────────────────────────

    @Override
    public Mono<UserLanguagePreference> get(UUID userId) {
        return repository.findByUserId(userId);
    }

    @Override
    public Mono<UserLanguagePreference> choose(UUID userId, String language) {
        return Mono.defer(() -> repository.upsert(userId, requireSupported(language), UserLanguagePreference.SOURCE_USER))
                .doOnNext(p -> cache.put(userId, p.language()));
    }

    @Override
    public Mono<UserLanguagePreference> detect(UUID userId, String language) {
        return Mono.defer(() -> repository.insertIfAbsent(userId, requireSupported(language),
                        UserLanguagePreference.SOURCE_DETECTED))
                .doOnNext(p -> cache.put(userId, p.language()));
    }

    private static String requireSupported(String language) {
        String code = LanguageTags.toPublicCode(language);
        if (code == null || !LanguageTags.isSupported(code)) {
            throw new IllegalArgumentException("unsupported language '" + language + "' (expected "
                    + LanguageTags.supported() + ")");
        }
        return code;
    }

    private static UUID parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
