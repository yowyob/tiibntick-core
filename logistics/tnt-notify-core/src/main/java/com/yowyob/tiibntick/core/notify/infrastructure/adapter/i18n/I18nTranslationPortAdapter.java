package com.yowyob.tiibntick.core.notify.infrastructure.adapter.i18n;

import com.yowyob.kernel.i18n.application.port.in.TranslateMessageUseCase;
import com.yowyob.kernel.i18n.config.I18nKernelProperties;
import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;
import com.yowyob.tiibntick.core.notify.application.port.out.ITranslationPort;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Bridges yow-i18n-kernel's {@link TranslateMessageUseCase} into tnt-notify-core's
 * {@link ITranslationPort}. Anti-corruption layer isolating the notification domain
 * from the i18n kernel internals.
 *
 * <p>This is the single choke point every notification send goes through
 * ({@code NotificationService#send} calls {@link #translate(NotificationModel)}
 * unconditionally) — the Kafka consumers, the incident/market adapters, and the
 * REST {@code NotificationController} all end up here. Fixing language handling
 * and the missing-translation fallback in this one place fixes it for all of them.
 *
 * <h2>Language normalization (Audit n°4, P-2)</h2>
 * Callers frequently pass a bare ISO code (e.g. {@code "fr"}) while the JSON
 * locale packs are indexed by {@link SupportedLanguage} tags (e.g. {@code "fr_CM"},
 * {@code "fr_FR"}), so a raw lookup was always a guaranteed miss. This adapter
 * resolves the requested language via {@link SupportedLanguage#fromCode(String)}
 * before looking the key up.
 *
 * <h2>Missing-translation fallback (Audit n°4, P0 item1)</h2>
 * If the key is still not found for the normalized language, the lookup is
 * retried once against the platform default locale
 * ({@link I18nKernelProperties#getDefaultLanguage()}). If the key is missing from
 * both, the raw {@code "⚠ Missing translation: ..."} text is never sent to the
 * end client — the miss is logged and counted via {@code tnt.notify.i18n.missing_translation},
 * and a safe, generic, non-technical message is returned instead.
 *
 * @author MANFOUO Braun
 */
public class I18nTranslationPortAdapter implements ITranslationPort {

    private static final Logger log = LoggerFactory.getLogger(I18nTranslationPortAdapter.class);

    private static final String SAFE_FALLBACK_MESSAGE_FR = "Vous avez une nouvelle notification.";
    private static final String SAFE_FALLBACK_MESSAGE_EN = "You have a new notification.";

    private final TranslateMessageUseCase translateMessageUseCase;
    private final String defaultLanguageTag;
    private final Counter missingTranslationCounter;

    public I18nTranslationPortAdapter(TranslateMessageUseCase translateMessageUseCase,
            I18nKernelProperties i18nKernelProperties,
            MeterRegistry meterRegistry) {
        this.translateMessageUseCase = translateMessageUseCase;
        this.defaultLanguageTag = i18nKernelProperties.getDefaultLanguage().getTag();
        this.missingTranslationCounter = Counter.builder("tnt.notify.i18n.missing_translation")
                .description("Notification template lookups that missed both the requested "
                        + "and default locale packs and fell back to the safe generic message")
                .register(meterRegistry);
    }

    @Override
    public Mono<String> translate(NotificationModel model) {
        String requestedLanguage = model.targetLanguage();
        String normalizedTag = normalize(requestedLanguage);

        return lookup(model, normalizedTag)
                .switchIfEmpty(Mono.defer(() -> {
                    if (defaultLanguageTag.equalsIgnoreCase(normalizedTag)) {
                        return Mono.empty();
                    }
                    log.warn("i18n: key '{}' not found for language '{}' (normalized to '{}'); "
                            + "retrying with default locale '{}'",
                            model.templateKey(), requestedLanguage, normalizedTag, defaultLanguageTag);
                    return lookup(model, defaultLanguageTag);
                }))
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    missingTranslationCounter.increment();
                    log.error("i18n: key '{}' missing from both '{}' and default '{}' locale packs — "
                            + "returning safe generic fallback, never the raw key/technical text",
                            model.templateKey(), normalizedTag, defaultLanguageTag);
                    return safeFallbackFor(normalizedTag);
                }));
    }

    private Mono<String> lookup(NotificationModel model, String languageTag) {
        return Mono.justOrEmpty(
                translateMessageUseCase.translate(model.templateKey(), languageTag, model.parameters()));
    }

    /**
     * Resolves the requested language to a pack-indexed tag, falling back to the
     * platform default when the requested code doesn't map to any
     * {@link SupportedLanguage} (including when it is {@code null}/blank).
     */
    private String normalize(String requestedLanguage) {
        SupportedLanguage resolved = SupportedLanguage.fromCode(requestedLanguage);
        return resolved != null ? resolved.getTag() : defaultLanguageTag;
    }

    /**
     * Generic, non-technical fallback text — deliberately never contains the
     * template key or the word "translation" so it never leaks internal
     * plumbing details to an end user.
     */
    private static String safeFallbackFor(String languageTag) {
        return languageTag != null && languageTag.toLowerCase().startsWith("en")
                ? SAFE_FALLBACK_MESSAGE_EN
                : SAFE_FALLBACK_MESSAGE_FR;
    }
}
