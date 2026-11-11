package com.yowyob.kernel.i18n.application.service;

import com.yowyob.kernel.i18n.application.port.out.MessageCatalogPort;
import com.yowyob.kernel.i18n.application.port.out.MessageTranslationPort;
import com.yowyob.kernel.i18n.domain.format.MessageFormatter;
import com.yowyob.kernel.i18n.domain.model.MessageEntry;
import com.yowyob.kernel.i18n.domain.model.MessageVariant;
import com.yowyob.kernel.i18n.domain.vo.LanguageTags;
import com.yowyob.kernel.i18n.domain.vo.LocalizedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory view of the message catalog, the single read path for every consumer.
 *
 * <p>Messages live in the database so they can be corrected without a release, but lookups happen on
 * every notification, API error and page load: the live rows are held in an immutable snapshot that is
 * swapped atomically. It is rebuilt on start-up, immediately after an admin change on this instance, and
 * by {@link #refreshIfChanged()} (a one-row revision probe) for changes made by other instances.
 *
 * <p>Resolution is <b>language-major</b>: for each language of the fallback chain (regional tag, public
 * language, platform default) the requested channel variant is tried, then {@code DEFAULT}; only then does
 * the next language apply. A recipient therefore never gets another language's text when their own has a
 * plain version.
 *
 * <p>Also implements the legacy synchronous {@link MessageTranslationPort}, so existing callers of
 * {@code TranslateMessageUseCase} transparently read the database.
 */
public class MessageCatalog implements MessageTranslationPort {

    private static final Logger log = LoggerFactory.getLogger(MessageCatalog.class);

    /** Immutable, swapped as a whole. */
    private record Snapshot(
            Map<String, String> exact,                       // ns|key|lang|variant -> text
            Map<String, List<String>> namespacesByKey,       // key -> namespaces defining it (sorted)
            Map<String, Map<String, Map<String, String>>> bundles, // lang -> ns -> key -> DEFAULT text
            String revision) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), "");
    }

    private final MessageCatalogPort port;
    private final MessageTranslationPort fallback;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.EMPTY);

    /**
     * @param fallback consulted only when the catalog has no text at all for the key (empty database,
     *                 key not seeded yet); typically the classpath JSON packs. May be {@code null}.
     */
    public MessageCatalog(MessageCatalogPort port, MessageTranslationPort fallback) {
        this.port = port;
        this.fallback = fallback;
    }

    // ── loading ────────────────────────────────────────────────────────────

    /** Rebuilds the snapshot from the database. Errors keep the previous snapshot. */
    public Mono<Void> reload() {
        return port.findAllLive().collectList()
                .zipWith(port.revision().defaultIfEmpty(""))
                .doOnNext(t -> {
                    snapshot.set(build(t.getT1(), t.getT2()));
                    log.info("i18n catalog loaded: {} live entries (revision {})", t.getT1().size(), t.getT2());
                })
                .then()
                .onErrorResume(e -> {
                    log.error("i18n catalog reload failed, keeping the previous snapshot: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /** Reloads only when another instance (or SQL) changed the catalog since the last load. */
    public Mono<Boolean> refreshIfChanged() {
        return port.revision().defaultIfEmpty("")
                .flatMap(rev -> rev.equals(snapshot.get().revision())
                        ? Mono.just(false)
                        : reload().thenReturn(true))
                .onErrorResume(e -> {
                    log.warn("i18n catalog revision probe failed: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /** Opaque marker that changes whenever the loaded content changes; usable as an HTTP ETag. */
    public String revision() {
        return snapshot.get().revision();
    }

    public int size() {
        return snapshot.get().exact().size();
    }

    private static Snapshot build(Collection<MessageEntry> entries, String revision) {
        Map<String, String> exact = new HashMap<>();
        Map<String, List<String>> nsByKey = new HashMap<>();
        Map<String, Map<String, Map<String, String>>> bundles = new HashMap<>();
        for (MessageEntry e : entries) {
            exact.put(exactKey(e.namespace(), e.key(), e.language(), e.variant()), e.text());
            if (MessageVariant.DEFAULT.equals(e.variant())) {
                List<String> list = nsByKey.computeIfAbsent(e.key(), k -> new ArrayList<>());
                if (!list.contains(e.namespace())) {
                    list.add(e.namespace());
                }
                bundles.computeIfAbsent(e.language(), l -> new TreeMap<>())
                        .computeIfAbsent(e.namespace(), n -> new TreeMap<>())
                        .put(e.key(), e.text());
            }
        }
        nsByKey.values().forEach(java.util.Collections::sort);
        return new Snapshot(Map.copyOf(exact), Map.copyOf(nsByKey), Map.copyOf(bundles), revision);
    }

    private static String exactKey(String ns, String key, String lang, String variant) {
        return ns + '|' + key + '|' + lang + '|' + variant;
    }

    // ── reading ────────────────────────────────────────────────────────────

    /**
     * The raw (unrendered) text for a key, following the fallback chain and channel variant rules.
     *
     * @param namespace the namespace, or {@code null} to search every namespace that defines {@code key}
     */
    public Optional<String> resolve(String namespace, String key, String language, String variant) {
        Snapshot s = snapshot.get();
        List<String> namespaces = namespace != null && !namespace.isBlank()
                ? List.of(namespace)
                : s.namespacesByKey().getOrDefault(key, List.of());
        String wanted = MessageVariant.normalize(variant);
        for (String lang : LanguageTags.fallbackChain(language)) {
            for (String ns : namespaces) {
                String text = s.exact().get(exactKey(ns, key, lang, wanted));
                if (text == null && !MessageVariant.DEFAULT.equals(wanted)) {
                    text = s.exact().get(exactKey(ns, key, lang, MessageVariant.DEFAULT));
                }
                if (text != null) {
                    return Optional.of(text);
                }
            }
        }
        return Optional.empty();
    }

    /** Resolved and rendered for one recipient: {@code {{params}}}, plural and typed formats applied. */
    public Optional<String> render(String namespace, String key, String language, String variant,
                                   Map<String, ?> parameters) {
        return resolve(namespace, key, language, variant)
                .map(text -> MessageFormatter.format(text, language, parameters));
    }

    /**
     * All DEFAULT texts of the given namespaces for a language, base languages first so a regional
     * override replaces the public text and a missing translation shows the default language instead
     * of a raw key. This is what the frontends download.
     */
    public Map<String, Map<String, String>> bundle(String language, Collection<String> namespaces) {
        Snapshot s = snapshot.get();
        List<String> chain = new ArrayList<>(LanguageTags.fallbackChain(language));
        java.util.Collections.reverse(chain); // default first, most specific last (it wins)
        Map<String, Map<String, String>> result = new TreeMap<>();
        for (String ns : namespaces) {
            Map<String, String> merged = new TreeMap<>();
            for (String lang : chain) {
                Map<String, String> part = s.bundles().getOrDefault(lang, Map.of()).get(ns);
                if (part != null) {
                    merged.putAll(part);
                }
            }
            result.put(ns, merged);
        }
        return result;
    }

    /** Every namespace that has at least one live message. */
    public List<String> namespaces() {
        return snapshot.get().bundles().values().stream()
                .flatMap(m -> m.keySet().stream()).distinct().sorted().toList();
    }

    // ── legacy synchronous port ────────────────────────────────────────────

    @Override
    public Optional<LocalizedMessage> findByKeyAndLanguage(String key, String language) {
        return findByKeyAndLanguage(key, language, MessageVariant.DEFAULT);
    }

    @Override
    public Optional<LocalizedMessage> findByKeyAndLanguage(String key, String language, String variant) {
        Optional<String> text = resolve(null, key, language, variant);
        if (text.isPresent()) {
            return Optional.of(new LocalizedMessage(key, language, text.get()));
        }
        if (fallback == null) {
            return Optional.empty();
        }
        for (String lang : LanguageTags.fallbackChain(language)) {
            Optional<LocalizedMessage> found = fallback.findByKeyAndLanguage(key, lang, variant);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
