package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort.DeliveryReceipt;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort.OutboundMessage;
import com.yowyob.tiibntick.core.notify.application.port.out.IPublishNotificationEventPort;
import com.yowyob.tiibntick.core.notify.application.port.out.ITranslationPort;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** A template can use what the service knows of the recipient, and the caller's own values still win. */
class NotificationRecipientContextTest {

    @Test
    void theTemplateReceivesTheRecipientContextAlongsideTheCallersParameters() {
        AtomicReference<NotificationModel> translated = new AtomicReference<>();
        AtomicReference<OutboundMessage> delivered = new AtomicReference<>();
        ITranslationPort translation = new ITranslationPort() {
            @Override
            public Mono<String> translate(NotificationModel model) {
                translated.set(model);
                return Mono.just("rendered");
            }

            @Override
            public Mono<String> rawTemplate(String templateKey, String language) {
                return Mono.empty();
            }
        };
        INotificationDeliveryPort delivery = message -> {
            delivered.set(message);
            return Mono.just(new DeliveryReceipt("d-1", "SENT"));
        };
        IPublishNotificationEventPort events = new IPublishNotificationEventPort() {
            @Override
            public Mono<Void> publishNotificationSent(Notification n) {
                return Mono.empty();
            }

            @Override
            public Mono<Void> publishNotificationFailed(Notification n) {
                return Mono.empty();
            }
        };
        NotificationService service = new NotificationService(translation, delivery, events,
                (recipientId, hint) -> Mono.just("en"));

        NotificationModel model = new NotificationModel("account.welcome", "fr",
                Map.of("name", "Ada", "recipientLanguage", "kept-by-caller"), NotificationPriority.NORMAL);

        StepVerifier.create(service.send("t", "o", "user-1", "ada@x.com", model, NotificationChannel.EMAIL))
                .expectNextCount(1).verifyComplete();

        assertThat(translated.get().targetLanguage()).isEqualTo("en");
        assertThat(translated.get().parameters())
                .containsEntry("name", "Ada")
                .containsEntry("recipientId", "user-1")
                .containsEntry("recipientAddress", "ada@x.com")
                // the caller's own value wins over the one the service would have added
                .containsEntry("recipientLanguage", "kept-by-caller");
        assertThat(delivered.get().variables()).containsEntry("recipientId", "user-1");
    }

    @Test
    void aMissingAddressOrIdIsSimplyNotAdded() {
        Map<String, Object> enriched = NotificationService.withRecipientContext(null, null, " ", "fr");
        assertThat(enriched).containsOnlyKeys("recipientLanguage");
    }
}
