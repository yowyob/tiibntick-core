package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort.DeliveryReceipt;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort.OutboundMessage;
import com.yowyob.tiibntick.core.notify.application.port.out.IPublishNotificationEventPort;
import com.yowyob.tiibntick.core.notify.application.port.out.IRecipientLanguagePort;
import com.yowyob.tiibntick.core.notify.application.port.out.ITranslationPort;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same business event notifies a client who reads English and a delivery person who reads French: each
 * one must get the message in their own account language, from one shared model.
 */
class NotificationRecipientLanguageTest {

    private static final String CLIENT = "00000000-0000-4000-8000-0000000000c1";
    private static final String DRIVER = "00000000-0000-4000-8000-0000000000d1";

    private final List<OutboundMessage> sent = new ArrayList<>();
    private NotificationService service;

    /** Echoes language and variant so the test sees exactly what was asked of the catalog. */
    private final ITranslationPort translation = new ITranslationPort() {
        @Override
        public Mono<String> translate(NotificationModel model) {
            return translate(model, null);
        }

        @Override
        public Mono<String> translate(NotificationModel model, NotificationChannel channel) {
            return Mono.just(model.targetLanguage() + "|" + channel + "|" + model.parameters().get("package_id"));
        }

        @Override
        public Mono<String> translateSubject(NotificationModel model, NotificationChannel channel) {
            return channel == NotificationChannel.EMAIL
                    ? Mono.just("subject-" + model.targetLanguage()) : Mono.empty();
        }

        @Override
        public Mono<String> rawTemplate(String templateKey, String language) {
            return Mono.empty();
        }
    };

    @BeforeEach
    void setUp() {
        INotificationDeliveryPort delivery = m -> {
            sent.add(m);
            return Mono.just(new DeliveryReceipt("d-" + sent.size(), "SENT"));
        };
        IPublishNotificationEventPort events = new IPublishNotificationEventPort() {
            @Override
            public Mono<Void> publishNotificationSent(com.yowyob.tiibntick.core.notify.domain.model.Notification n) {
                return Mono.empty();
            }

            @Override
            public Mono<Void> publishNotificationFailed(com.yowyob.tiibntick.core.notify.domain.model.Notification n) {
                return Mono.empty();
            }
        };
        IRecipientLanguagePort languages = (recipientId, hint) -> Mono.just(switch (recipientId) {
            case CLIENT -> "en";
            case DRIVER -> "fr";
            default -> hint == null ? "fr" : hint;
        });
        service = new NotificationService(translation, delivery, events, languages);
    }

    private final NotificationModel sharedModel = new NotificationModel(
            "notification.package.delivered", "fr", Map.of("package_id", "PKG-7"), NotificationPriority.NORMAL);

    @Test
    @DisplayName("one model, two recipients: each message is rendered in its recipient's own language")
    void eachRecipientReadsTheirOwnLanguage() {
        StepVerifier.create(service.send("t", "o", CLIENT, CLIENT, sharedModel, NotificationChannel.IN_APP_WEBSOCKET)
                .then(service.send("t", "o", DRIVER, DRIVER, sharedModel, NotificationChannel.IN_APP_WEBSOCKET)))
                .expectNextCount(1).verifyComplete();

        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).recipientId()).isEqualTo(CLIENT);
        assertThat(sent.get(0).body()).startsWith("en|");
        assertThat(sent.get(0).locale()).isEqualTo("en");
        assertThat(sent.get(1).recipientId()).isEqualTo(DRIVER);
        assertThat(sent.get(1).body()).startsWith("fr|");
    }

    @Test
    @DisplayName("the recipient's account language wins over the language the caller put on the model")
    void accountLanguageBeatsTheCallersLanguage() {
        NotificationModel frenchModel = NotificationModel.of("k", "fr", Map.of("package_id", "1"));
        StepVerifier.create(service.send("t", "o", CLIENT, CLIENT, frenchModel, NotificationChannel.PUSH_FCM))
                .expectNextCount(1).verifyComplete();
        assertThat(sent.get(0).body()).startsWith("en|PUSH_FCM");
    }

    @Test
    @DisplayName("an unknown recipient (e.g. a bare e-mail address) falls back to the language of the model")
    void unknownRecipientUsesTheHint() {
        NotificationModel english = NotificationModel.of("k", "en", Map.of("package_id", "1"));
        StepVerifier.create(service.send("t", "o", "someone@example.com", "someone@example.com", english,
                NotificationChannel.EMAIL)).expectNextCount(1).verifyComplete();
        assertThat(sent.get(0).body()).startsWith("en|");
    }

    @Test
    @DisplayName("an e-mail carries a subject in the recipient's language; other channels carry none")
    void emailSubjectFollowsTheRecipient() {
        StepVerifier.create(service.send("t", "o", CLIENT, "c@example.com", sharedModel, NotificationChannel.EMAIL)
                .then(service.send("t", "o", DRIVER, DRIVER, sharedModel, NotificationChannel.PUSH_FCM)))
                .expectNextCount(1).verifyComplete();
        assertThat(sent.get(0).subject()).isEqualTo("subject-en");
        assertThat(sent.get(1).subject()).isNull();
    }

    @Test
    @DisplayName("the stored notification keeps the body of its own recipient")
    void notificationContentIsPerRecipient() {
        StepVerifier.create(service.send("t", "o", DRIVER, DRIVER, sharedModel, NotificationChannel.SMS_LOCAL))
                .assertNext(n -> assertThat(n.getContent()).startsWith("fr|SMS_LOCAL")).verifyComplete();
    }
}
