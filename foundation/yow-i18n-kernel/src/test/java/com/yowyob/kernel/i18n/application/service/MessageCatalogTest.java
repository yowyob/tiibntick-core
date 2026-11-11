package com.yowyob.kernel.i18n.application.service;

import com.yowyob.kernel.i18n.application.port.out.MessageCatalogPort;
import com.yowyob.kernel.i18n.application.port.out.MessageTranslationPort;
import com.yowyob.kernel.i18n.domain.model.MessageEntry;
import com.yowyob.kernel.i18n.domain.model.MessageVariant;
import com.yowyob.kernel.i18n.domain.model.MessageVersion;
import com.yowyob.kernel.i18n.domain.model.MissingTranslation;
import com.yowyob.kernel.i18n.domain.vo.LocalizedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MessageCatalogTest {

    /** Minimal in-memory port: only what the cache reads. */
    private static final class FakePort implements MessageCatalogPort {
        final List<MessageEntry> rows = new ArrayList<>();
        final AtomicInteger loads = new AtomicInteger();
        boolean failing;

        void put(String ns, String key, String lang, String variant, String text) {
            rows.add(new MessageEntry(UUID.randomUUID(), ns, key, lang, variant, text, null, 1, Instant.now(), "t"));
        }

        @Override public Flux<MessageEntry> findAllLive() {
            loads.incrementAndGet();
            return failing ? Flux.error(new IllegalStateException("db down")) : Flux.fromIterable(List.copyOf(rows));
        }
        @Override public Mono<String> revision() {
            return failing ? Mono.error(new IllegalStateException("db down")) : Mono.just(String.valueOf(rows.size()));
        }
        @Override public Flux<MessageEntry> search(String n, String k, String l, boolean d, int a, int b) { return Flux.empty(); }
        @Override public Mono<MessageEntry> find(String n, String k, String l, String v) { return Mono.empty(); }
        @Override public Mono<MessageEntry> save(String n, String k, String l, String v, String t, boolean p, String a) { return Mono.empty(); }
        @Override public Mono<MessageEntry> publishDraft(String n, String k, String l, String v, String a) { return Mono.empty(); }
        @Override public Flux<MessageVersion> history(String n, String k, String l, String v) { return Flux.empty(); }
        @Override public Flux<MissingTranslation> findMissing(String n, List<String> l) { return Flux.empty(); }
    }

    private FakePort port;
    private MessageCatalog catalog;

    @BeforeEach
    void setUp() {
        port = new FakePort();
        port.put("notify", "parcel.delivered", "fr", "DEFAULT", "Le colis {{id}} a été livré.");
        port.put("notify", "parcel.delivered", "en", "DEFAULT", "Parcel {{id}} was delivered.");
        port.put("notify", "parcel.delivered", "en", "SMS", "Parcel {{id}} delivered");
        port.put("notify", "only.french", "fr", "DEFAULT", "Seulement en français");
        port.put("notify", "count", "fr", "DEFAULT", "{{n:plural|one=# colis|other=# colis}}");
        port.put("notify", "count", "en", "DEFAULT", "{{n:plural|one=# parcel|other=# parcels}}");
        port.put("ui", "parcel.delivered", "fr", "DEFAULT", "Livré");
        port.put("ui", "title", "fr", "DEFAULT", "Titre");
        port.put("ui", "title", "en", "DEFAULT", "Title");
        port.put("ui", "title", "en_CM", "DEFAULT", "Title (Cameroon)");
        catalog = new MessageCatalog(port, null);
        catalog.reload().block();
    }

    @Test
    void eachRecipientGetsTheirOwnLanguage() {
        Map<String, Object> p = Map.of("id", "PKG-1");
        assertThat(catalog.render("notify", "parcel.delivered", "fr", null, p)).contains("Le colis PKG-1 a été livré.");
        assertThat(catalog.render("notify", "parcel.delivered", "en", null, p)).contains("Parcel PKG-1 was delivered.");
    }

    @Test
    void aMissingTranslationFallsBackToTheDefaultLanguageNeverToARawKey() {
        assertThat(catalog.render("notify", "only.french", "en", null, Map.of())).contains("Seulement en français");
        assertThat(catalog.resolve("notify", "does.not.exist", "en", null)).isEmpty();
    }

    @Test
    void channelVariantIsPreferredThenPlainTextOfTheSameLanguage() {
        assertThat(catalog.resolve("notify", "parcel.delivered", "en", "SMS")).contains("Parcel {{id}} delivered");
        // fr has no SMS variant: the plain French text wins over the English SMS.
        assertThat(catalog.resolve("notify", "parcel.delivered", "fr", "SMS")).contains("Le colis {{id}} a été livré.");
    }

    @Test
    void regionalOverrideWinsThenPublicLanguage() {
        assertThat(catalog.resolve("ui", "title", "en_CM", null)).contains("Title (Cameroon)");
        assertThat(catalog.resolve("ui", "title", "en", null)).contains("Title");
        assertThat(catalog.resolve("ui", "title", "fr_CM", null)).contains("Titre");
    }

    @Test
    void pluralIsAppliedInTheRecipientsLanguage() {
        assertThat(catalog.render("notify", "count", "fr", null, Map.of("n", 0))).contains("0 colis");
        assertThat(catalog.render("notify", "count", "en", null, Map.of("n", 0))).contains("0 parcels");
        assertThat(catalog.render("notify", "count", "en", null, Map.of("n", 1))).contains("1 parcel");
    }

    @Test
    void withoutANamespaceTheFirstNamespaceDefiningTheKeyIsUsed() {
        // "notify" sorts before "ui".
        assertThat(catalog.resolve(null, "parcel.delivered", "fr", null)).contains("Le colis {{id}} a été livré.");
    }

    @Test
    void bundleMergesDefaultUnderTheRequestedLanguage() {
        Map<String, Map<String, String>> bundle = catalog.bundle("en", List.of("ui", "notify"));
        assertThat(bundle.get("ui")).containsEntry("title", "Title").containsEntry("parcel.delivered", "Livré");
        // regional override is on top for en_CM
        assertThat(catalog.bundle("en_CM", List.of("ui")).get("ui")).containsEntry("title", "Title (Cameroon)");
        assertThat(catalog.bundle("fr", List.of("nope")).get("nope")).isEmpty();
        assertThat(catalog.namespaces()).containsExactly("notify", "ui");
    }

    @Test
    void refreshReloadsOnlyWhenTheRevisionMoved() {
        int before = port.loads.get();
        assertThat(catalog.refreshIfChanged().block()).isFalse();
        assertThat(port.loads.get()).isEqualTo(before);

        port.put("ui", "new.key", "fr", "DEFAULT", "Nouveau");
        assertThat(catalog.refreshIfChanged().block()).isTrue();
        assertThat(catalog.resolve("ui", "new.key", "fr", null)).contains("Nouveau");
    }

    @Test
    void aFailingDatabaseKeepsTheLastGoodSnapshot() {
        port.failing = true;
        catalog.reload().block();
        assertThat(catalog.refreshIfChanged().block()).isFalse();
        assertThat(catalog.resolve("notify", "parcel.delivered", "fr", null)).isPresent();
    }

    @Test
    void packagedMessagesAnswerWhenTheCatalogHasNothing() {
        MessageTranslationPort json = new MessageTranslationPort() {
            @Override public Optional<LocalizedMessage> findByKeyAndLanguage(String key, String language) {
                return "fr".equals(language) && "legacy.key".equals(key)
                        ? Optional.of(new LocalizedMessage(key, language, "Packagé")) : Optional.empty();
            }
        };
        MessageCatalog withFallback = new MessageCatalog(new FakePort(), json);
        withFallback.reload().block();
        // English asked, only French packaged: the default language of the chain is tried too.
        assertThat(withFallback.findByKeyAndLanguage("legacy.key", "en")).map(LocalizedMessage::content).contains("Packagé");
        assertThat(withFallback.findByKeyAndLanguage("nope", "en")).isEmpty();
    }

    @Test
    void legacyPortFindsMessagesThroughTheCatalog() {
        assertThat(catalog.findByKeyAndLanguage("title", "en")).map(LocalizedMessage::content).contains("Title");
        assertThat(MessageVariant.isValid("SMS")).isTrue();
    }
}
