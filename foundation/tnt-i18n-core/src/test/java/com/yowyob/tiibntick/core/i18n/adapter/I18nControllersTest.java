package com.yowyob.tiibntick.core.i18n.adapter;

import com.yowyob.kernel.i18n.application.port.out.MessageCatalogPort;
import com.yowyob.kernel.i18n.application.service.MessageCatalog;
import com.yowyob.kernel.i18n.domain.model.MessageEntry;
import com.yowyob.tiibntick.core.i18n.adapter.in.web.I18nExceptionHandler;
import com.yowyob.tiibntick.core.i18n.adapter.in.web.I18nPublicController;
import com.yowyob.tiibntick.core.i18n.config.TntI18nProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class I18nControllersTest {

    private WebTestClient client;

    private static MessageEntry e(String ns, String key, String lang, String text) {
        return new MessageEntry(UUID.randomUUID(), ns, key, lang, "DEFAULT", text, null, 1, Instant.now(), "seed");
    }

    @BeforeEach
    void setUp() {
        MessageCatalogPort port = mock(MessageCatalogPort.class);
        when(port.findAllLive()).thenReturn(Flux.just(
                e("go", "nav.home", "fr", "Accueil"), e("go", "nav.home", "en", "Home"),
                e("go", "nav.only_fr", "fr", "Seulement en français"),
                e("notify", "secret.key", "fr", "interne")));
        when(port.revision()).thenReturn(Mono.just("4|rev1"));
        MessageCatalog catalog = new MessageCatalog(port, null);
        catalog.reload().block();
        client = WebTestClient.bindToController(
                        new I18nPublicController(catalog, new TntI18nProperties()), new I18nExceptionHandler())
                .build();
    }

    @Test
    void languagesListFrenchAndEnglishWithFrenchAsDefault() {
        client.get().uri("/api/v1/i18n/languages").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.defaultLanguage").isEqualTo("fr")
                .jsonPath("$.languages[0].code").isEqualTo("fr")
                .jsonPath("$.languages[1].code").isEqualTo("en");
    }

    @Test
    void aBundleCarriesTheLanguageAndFallsBackToFrenchForAMissingTranslation() {
        client.get().uri("/api/v1/i18n/bundles/en?ns=go").exchange().expectStatus().isOk()
                .expectHeader().exists("ETag")
                .expectHeader().cacheControl(org.springframework.http.CacheControl.maxAge(
                        java.time.Duration.ofMinutes(5)).cachePublic().mustRevalidate())
                .expectBody()
                .jsonPath("$.language").isEqualTo("en")
                .jsonPath("$.namespaces.go['nav.home']").isEqualTo("Home")
                .jsonPath("$.namespaces.go['nav.only_fr']").isEqualTo("Seulement en français");
    }

    @Test
    void aRegionalTagIsFoldedToItsPublicLanguage() {
        client.get().uri("/api/v1/i18n/bundles/en_CM?ns=go").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.language").isEqualTo("en");
    }

    @Test
    void revalidationWithTheEtagAnswers304() {
        String etag = client.get().uri("/api/v1/i18n/bundles/fr?ns=go").exchange()
                .returnResult(String.class).getResponseHeaders().getETag();
        client.get().uri("/api/v1/i18n/bundles/fr?ns=go").header("If-None-Match", etag)
                .exchange().expectStatus().isNotModified();
    }

    @Test
    void serverSideNamespacesAreNeverServedToBrowsers() {
        client.get().uri("/api/v1/i18n/bundles/fr?ns=notify").exchange().expectStatus().isBadRequest();
    }

    @Test
    void anUnsupportedLanguageIsABadRequest() {
        client.get().uri("/api/v1/i18n/bundles/de").exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.error.code").isEqualTo("I18N_INVALID_REQUEST");
    }

    @Test
    void defaultNamespacesExcludeEverythingNotPublic() {
        client.get().uri("/api/v1/i18n/bundles/fr").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.namespaces.notify").doesNotExist()
                .jsonPath("$.namespaces.go['nav.home']").isEqualTo("Accueil");
    }
}
