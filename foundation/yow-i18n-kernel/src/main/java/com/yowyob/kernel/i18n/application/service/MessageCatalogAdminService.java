package com.yowyob.kernel.i18n.application.service;

import com.yowyob.kernel.i18n.application.port.out.MessageCatalogPort;
import com.yowyob.kernel.i18n.domain.model.MessageEntry;
import com.yowyob.kernel.i18n.domain.model.MessageVariant;
import com.yowyob.kernel.i18n.domain.model.MessageVersion;
import com.yowyob.kernel.i18n.domain.model.MissingTranslation;
import com.yowyob.kernel.i18n.domain.vo.LanguageTags;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Write side of the catalog: create/edit/publish messages, list drafts and missing translations, read the
 * version history. Every successful write refreshes the in-memory snapshot of this instance at once; other
 * instances pick the change up on their next revision probe.
 *
 * <p>Transport-agnostic and free of any security concern — the REST layer decides who may call it.
 */
public class MessageCatalogAdminService {

    static final int MAX_TEXT_LENGTH = 4000;
    private static final Pattern NAMESPACE = Pattern.compile("[a-z][a-z0-9_-]{0,39}");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}");

    private final MessageCatalogPort port;
    private final MessageCatalog catalog;

    public MessageCatalogAdminService(MessageCatalogPort port, MessageCatalog catalog) {
        this.port = port;
        this.catalog = catalog;
    }

    public Flux<MessageEntry> search(String namespace, String keyPrefix, String language, boolean draftsOnly,
                                     int limit, int offset) {
        return port.search(namespace, keyPrefix, normalizeLanguageOrNull(language), draftsOnly, limit, offset);
    }

    public Mono<MessageEntry> get(String namespace, String key, String language, String variant) {
        return port.find(namespace, key, requireLanguage(language), variant);
    }

    /** Creates or edits a message; {@code publish=false} keeps the edit as a draft. */
    public Mono<MessageEntry> save(String namespace, String key, String language, String variant, String text,
                                   boolean publish, String actor) {
        return Mono.defer(() -> {
            validateAddress(namespace, key, variant);
            String lang = requireLanguage(language);
            validateText(text);
            return port.save(namespace, key, lang, MessageVariant.normalize(variant), text, publish, actor);
        }).flatMap(saved -> publish ? catalog.reload().thenReturn(saved) : Mono.just(saved));
    }

    public Mono<MessageEntry> publishDraft(String namespace, String key, String language, String variant,
                                           String actor) {
        return Mono.defer(() -> {
            validateAddress(namespace, key, variant);
            return port.publishDraft(namespace, key, requireLanguage(language), variant, actor);
        }).flatMap(published -> catalog.reload().thenReturn(published));
    }

    public Flux<MessageVersion> history(String namespace, String key, String language, String variant) {
        return port.history(namespace, key, requireLanguage(language), variant);
    }

    /** Keys translated in some supported language but missing in another one. */
    public Flux<MissingTranslation> missing(String namespace) {
        return port.findMissing(namespace, LanguageTags.supported());
    }

    // ── validation ─────────────────────────────────────────────────────────

    private static void validateAddress(String namespace, String key, String variant) {
        if (namespace == null || !NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("namespace must match " + NAMESPACE.pattern());
        }
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("key must match " + KEY.pattern());
        }
        if (variant != null && !variant.isBlank() && !MessageVariant.isValid(MessageVariant.normalize(variant))) {
            throw new IllegalArgumentException("unknown variant '" + variant + "'");
        }
    }

    private static void validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("text is longer than " + MAX_TEXT_LENGTH + " characters");
        }
        int open = 0;
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '{' && text.charAt(i + 1) == '{') {
                open++;
                i++;
            } else if (text.charAt(i) == '}' && text.charAt(i + 1) == '}') {
                open--;
                i++;
                if (open < 0) {
                    break;
                }
            }
        }
        if (open != 0) {
            throw new IllegalArgumentException("unbalanced {{ }} placeholder in text");
        }
    }

    private static String requireLanguage(String language) {
        String normalized = normalizeLanguageOrNull(language);
        if (normalized == null) {
            throw new IllegalArgumentException("unsupported language '" + language + "' (expected "
                    + LanguageTags.supported() + " or a regional tag of them)");
        }
        return normalized;
    }

    private static String normalizeLanguageOrNull(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        return LanguageTags.toPublicCode(language) == null ? null : LanguageTags.normalize(language);
    }

    /** Languages the admin may write to, for UIs. */
    public List<String> languages() {
        return LanguageTags.supported();
    }
}
