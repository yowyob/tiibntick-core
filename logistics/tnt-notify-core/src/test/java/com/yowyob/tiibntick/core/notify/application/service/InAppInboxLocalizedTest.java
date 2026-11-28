package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.application.port.out.IInAppInboxPort;
import com.yowyob.tiibntick.core.notify.application.port.out.IRecipientLanguagePort;
import com.yowyob.tiibntick.core.notify.application.port.out.ITranslationPort;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.model.InAppMessage;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Maintainer directive of 2026-11-27: an in-app message rendered in the recipient's account language, title from the e-mail subject. */
class InAppInboxLocalizedTest {

    final UUID owner = UUID.randomUUID();
    final AtomicReference<InAppMessage> written = new AtomicReference<>();

    final IInAppInboxPort port = new IInAppInboxPort() {
        public Mono<InAppMessage> send(UUID r, String app, String title, String body, Map<String, String> meta) {
            InAppMessage m = new InAppMessage(UUID.randomUUID(), r, title, body, meta, Instant.now(), null);
            written.set(m);
            return Mono.just(m);
        }
        public Flux<InAppMessage> mine(String application) { return Flux.empty(); }
        public Mono<InAppMessage> markRead(UUID id) { return Mono.empty(); }
    };

    /** Renders "<variant>/<language>/<key>/<userId>" so the test sees exactly what was asked. */
    final ITranslationPort translations = new ITranslationPort() {
        public Mono<String> translate(NotificationModel m) { return translate(m, null); }
        public Mono<String> translate(NotificationModel m, NotificationChannel c) {
            return Mono.just("body/" + c + "/" + m.targetLanguage() + "/" + m.templateKey() + "/" + m.parameters().get("userId"));
        }
        public Mono<String> translateSubject(NotificationModel m, NotificationChannel c) {
            return m.templateKey().endsWith(".nosubject") ? Mono.empty()
                    : Mono.just("subject/" + c + "/" + m.targetLanguage());
        }
        public Mono<String> rawTemplate(String k, String l) { return Mono.empty(); }
    };

    InAppInboxService service(IRecipientLanguagePort languages) {
        InAppInboxService s = new InAppInboxService(port);
        s.setLocalization(translations, languages);
        return s;
    }

    @Test
    void rendersInTheAccountLanguageNotTheHint() {
        IRecipientLanguagePort accountSaysEnglish = (id, hint) -> Mono.just(owner.toString().equals(id) ? "en" : hint);
        StepVerifier.create(service(accountSaysEnglish).sendLocalized(owner, "tiibntick-admin", "k.granted",
                        Map.of("userId", "u1"), "fr", Map.of("grantId", "g1")))
                .expectNextCount(1).verifyComplete();
        assertThat(written.get().recipientUserId()).isEqualTo(owner);
        assertThat(written.get().body()).isEqualTo("body/IN_APP_WEBSOCKET/en/k.granted/u1");
        assertThat(written.get().title()).isEqualTo("subject/EMAIL/en");
        assertThat(written.get().metadata()).containsEntry("language", "en").containsEntry("templateKey", "k.granted")
                .containsEntry("grantId", "g1");
    }

    @Test
    void withoutAStoredLanguageTheHintThenFrenchApply() {
        StepVerifier.create(service((id, hint) -> Mono.empty())
                        .sendLocalized(owner, "a", "k.nosubject", Map.of("userId", "u"), null, null))
                .expectNextCount(1).verifyComplete();
        assertThat(written.get().body()).isEqualTo("body/IN_APP_WEBSOCKET/fr/k.nosubject/u");
        assertThat(written.get().title()).isEqualTo(written.get().body());
    }

    @Test
    void refusesWhenNotWired() {
        StepVerifier.create(new InAppInboxService(port).sendLocalized(owner, "a", "k", Map.of(), null, null))
                .expectError(IllegalStateException.class).verify();
    }
}
