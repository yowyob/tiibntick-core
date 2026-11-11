package com.yowyob.kernel.i18n.application.port.out;

import com.yowyob.kernel.i18n.domain.model.MessageEntry;
import com.yowyob.kernel.i18n.domain.model.MessageVersion;
import com.yowyob.kernel.i18n.domain.model.MissingTranslation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/** Persistence of the message catalog. Implemented over R2DBC; the in-memory cache sits above it. */
public interface MessageCatalogPort {

    /** Every entry that has a live text (what consumers read). */
    Flux<MessageEntry> findAllLive();

    /** Cheap change marker ({@code count|max(updated_at)}); equal markers mean the cache is current. */
    Mono<String> revision();

    Flux<MessageEntry> search(String namespace, String keyPrefix, String language, boolean draftsOnly,
                              int limit, int offset);

    Mono<MessageEntry> find(String namespace, String key, String language, String variant);

    /**
     * Creates or updates an entry. With {@code publish} the text becomes live (version + 1, a history
     * row is written); without it the text is stored as a draft and the live text is untouched.
     */
    Mono<MessageEntry> save(String namespace, String key, String language, String variant, String text,
                            boolean publish, String actor);

    /** Promotes the pending draft to live text; empty when there is no draft. */
    Mono<MessageEntry> publishDraft(String namespace, String key, String language, String variant, String actor);

    Flux<MessageVersion> history(String namespace, String key, String language, String variant);

    /** Keys live in some language of {@code languages} but absent from another. */
    Flux<MissingTranslation> findMissing(String namespace, List<String> languages);
}
