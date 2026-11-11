package com.yowyob.tiibntick.core.notify.infrastructure.adapter.i18n;

import com.yowyob.kernel.i18n.application.port.out.MessageCatalogPort;
import com.yowyob.kernel.i18n.application.service.MessageCatalog;
import com.yowyob.kernel.i18n.application.service.TranslationService;
import com.yowyob.kernel.i18n.config.I18nKernelProperties;
import com.yowyob.kernel.i18n.domain.model.MessageEntry;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Notification texts come from the catalog, per channel flavour and per language, parameters filled in. */
class ChannelVariantTranslationTest {

    private I18nTranslationPortAdapter adapter;

    private static MessageEntry e(String lang, String variant, String text) {
        return new MessageEntry(UUID.randomUUID(), "notify", "order.paid", lang, variant, text, null, 1,
                Instant.now(), "seed");
    }

    @BeforeEach
    void setUp() {
        MessageCatalogPort port = mock(MessageCatalogPort.class);
        when(port.findAllLive()).thenReturn(Flux.just(
                e("fr", "DEFAULT", "Paiement de {{amount:money}} reçu pour la commande {{order}}."),
                e("fr", "SMS", "Paiement {{amount:money}} reçu ({{order}})"),
                e("fr", "EMAIL_SUBJECT", "Paiement reçu"),
                e("en", "DEFAULT", "Payment of {{amount:money}} received for order {{order}}."),
                e("en", "PUSH", "Paid: {{amount:money}}"),
                e("en", "EMAIL_SUBJECT", "Payment received")));
        when(port.revision()).thenReturn(Mono.just("6|r"));
        MessageCatalog catalog = new MessageCatalog(port, null);
        catalog.reload().block();
        adapter = new I18nTranslationPortAdapter(new TranslationService(catalog), new I18nKernelProperties(),
                new SimpleMeterRegistry());
    }

    private static NotificationModel model(String lang) {
        return NotificationModel.of("order.paid", lang, Map.of("amount", 1500, "order", "CMD-1"));
    }

    @Test
    void theChannelPicksItsOwnFlavourAndFormatsMoneyForTheLanguage() {
        StepVerifier.create(adapter.translate(model("fr"), NotificationChannel.SMS_LOCAL))
                .expectNextMatches(s -> s.replace(' ', ' ').replace(' ', ' ').equals("Paiement 1 500 XAF reçu (CMD-1)"))
                .verifyComplete();
        StepVerifier.create(adapter.translate(model("en"), NotificationChannel.PUSH_FCM))
                .expectNext("Paid: XAF 1,500").verifyComplete();
    }

    @Test
    void aChannelWithoutItsOwnFlavourUsesThePlainTextOfTheSameLanguage() {
        StepVerifier.create(adapter.translate(model("en"), NotificationChannel.SMS_LOCAL))
                .expectNext("Payment of XAF 1,500 received for order CMD-1.").verifyComplete();
    }

    @Test
    void emailSubjectIsOnlyReturnedWhenReallyWritten() {
        StepVerifier.create(adapter.translateSubject(model("en"), NotificationChannel.EMAIL))
                .expectNext("Payment received").verifyComplete();
        StepVerifier.create(adapter.translateSubject(model("fr"), NotificationChannel.EMAIL))
                .expectNext("Paiement reçu").verifyComplete();
        StepVerifier.create(adapter.translateSubject(model("fr"), NotificationChannel.SMS_LOCAL)).verifyComplete();
    }

    @Test
    void anUnknownKeyNeverLeaksTechnicalTextToTheUser() {
        StepVerifier.create(adapter.translate(NotificationModel.of("nope", "en", Map.of()), NotificationChannel.EMAIL))
                .expectNext("You have a new notification.").verifyComplete();
    }
}
