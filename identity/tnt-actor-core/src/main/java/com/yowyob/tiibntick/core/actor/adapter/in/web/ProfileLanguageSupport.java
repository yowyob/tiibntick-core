package com.yowyob.tiibntick.core.actor.adapter.in.web;

import com.yowyob.kernel.i18n.domain.vo.LanguageTags;
import com.yowyob.tiibntick.core.i18n.application.port.in.ManageUserLanguageUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Records the account language when a profile is created. The language is asked from the very first
 * step, so the creation request may carry it: it is then stored as an explicit choice of the account
 * (one language for every profile of the account). Without it, the browser language (Accept-Language) is
 * stored only if the account has none yet, and a previous choice is never overridden.
 *
 * <p>Never fails or blocks the creation: an unknown code or a storage error is logged and ignored.
 */
@Component
public class ProfileLanguageSupport {

    private static final Logger log = LoggerFactory.getLogger(ProfileLanguageSupport.class);

    private final ObjectProvider<ManageUserLanguageUseCase> languages;

    public ProfileLanguageSupport(ObjectProvider<ManageUserLanguageUseCase> languages) {
        this.languages = languages;
    }

    /**
     * @param chosen         language picked by the user in the creation form, may be {@code null}
     * @param acceptLanguage raw Accept-Language header, may be {@code null}
     */
    public Mono<Void> record(UUID userId, String chosen, String acceptLanguage) {
        ManageUserLanguageUseCase use = languages == null ? null : languages.getIfAvailable();
        if (use == null || userId == null) {
            return Mono.empty();
        }
        String explicit = LanguageTags.toPublicCode(chosen);
        Mono<?> store;
        if (explicit != null) {
            store = use.choose(userId, explicit);
        } else {
            String detected = LanguageTags.fromAcceptLanguage(acceptLanguage);
            if (detected == null) {
                return Mono.empty();
            }
            store = use.detect(userId, detected);
        }
        return store.then().onErrorResume(e -> {
            log.warn("Account language not stored for {} at profile creation: {}", userId, e.getMessage());
            return Mono.empty();
        });
    }
}
