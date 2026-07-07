package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.application.port.out.IMessageProviderPort;
import com.yowyob.tiibntick.core.notify.application.port.out.IPublishNotificationEventPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationRepositoryPort;
import com.yowyob.tiibntick.core.notify.application.port.out.ITranslationPort;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationId;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationService}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private INotificationRepositoryPort repository;
    @Mock private ITranslationPort translationPort;
    @Mock private IMessageProviderPort smsProvider;
    @Mock private IPublishNotificationEventPort eventPort;

    private NotificationService service;

    private static final String RECIPIENT_ID  = "user-42";
    private static final String PHONE_NUMBER     = "+237691000001";
    private static final String TRANSLATED_MSG   = "Package PKG-99 has been delivered.";

    @BeforeEach
    void setUp() {
        service = new NotificationService(
                repository, translationPort, List.of(smsProvider), eventPort);
    }

    @Test
    void send_shouldTranslate_persist_dispatch_andPublishEvent() {
        // given
        NotificationModel model = NotificationModel.of(
                "notification.package.delivered", "en_CM",
                Map.of("package_id", "PKG-99"));

        Notification persisted = new Notification(
                NotificationId.generate(), RECIPIENT_ID,
                NotificationChannel.SMS_LOCAL, TRANSLATED_MSG,
                NotificationPriority.NORMAL, DeliveryStatus.PENDING,
                Instant.now(), null, null, 0);

        Notification sent = new Notification(
                persisted.getId(), RECIPIENT_ID,
                NotificationChannel.SMS_LOCAL, TRANSLATED_MSG,
                NotificationPriority.NORMAL, DeliveryStatus.SENT,
                Instant.now(), Instant.now(), null, 0);

        when(translationPort.translate(model)).thenReturn(Mono.just(TRANSLATED_MSG));
        when(repository.save(any())).thenReturn(Mono.just(persisted)).thenReturn(Mono.just(sent));
        when(smsProvider.supports(NotificationChannel.SMS_LOCAL)).thenReturn(true);
        when(smsProvider.sendMessage(anyString(), anyString())).thenReturn(Mono.empty());
        when(eventPort.publishNotificationSent(any())).thenReturn(Mono.empty());

        // when / then
        Mono<Notification> result = service.send(
                RECIPIENT_ID, PHONE_NUMBER, model, NotificationChannel.SMS_LOCAL);
        StepVerifier.create(result)
                .expectNextMatches(n -> n.getStatus() == DeliveryStatus.SENT)
                .verifyComplete();
    }

    @Test
    void send_shouldMarkEchouee_whenNoProviderFound() {
        // given
        NotificationModel model = NotificationModel.of(
                "notification.package.delivered", "fr_CM", Map.of());

        Notification persisted = new Notification(
                NotificationId.generate(), RECIPIENT_ID,
                NotificationChannel.EMAIL, TRANSLATED_MSG,
                NotificationPriority.NORMAL, DeliveryStatus.PENDING,
                Instant.now(), null, null, 0);

        Notification failed = new Notification(
                persisted.getId(), RECIPIENT_ID,
                NotificationChannel.EMAIL, TRANSLATED_MSG,
                NotificationPriority.NORMAL, DeliveryStatus.FAILED,
                Instant.now(), null, "No provider configured for channel: EMAIL", 1);

        when(translationPort.translate(model)).thenReturn(Mono.just(TRANSLATED_MSG));
        when(repository.save(any())).thenReturn(Mono.just(persisted)).thenReturn(Mono.just(failed));
        // SMS provider does not support EMAIL
        when(smsProvider.supports(NotificationChannel.EMAIL)).thenReturn(false);
        when(eventPort.publishNotificationFailed(any())).thenReturn(Mono.empty());

        // when / then
        Mono<Notification> result = service.send(
                RECIPIENT_ID, "user@example.com", model, NotificationChannel.EMAIL);
        StepVerifier.create(result)
                .expectNextMatches(n -> n.getStatus() == DeliveryStatus.FAILED)
                .verifyComplete();
    }

    @Test
    void send_shouldMarkEchouee_whenProviderThrowsException() {
        // given
        NotificationModel model = NotificationModel.of(
                "notification.package.delivered", "fr_CM", Map.of());

        Notification persisted = new Notification(
                NotificationId.generate(), RECIPIENT_ID,
                NotificationChannel.SMS_LOCAL, TRANSLATED_MSG,
                NotificationPriority.NORMAL, DeliveryStatus.PENDING,
                Instant.now(), null, null, 0);

        Notification failed = new Notification(
                persisted.getId(), RECIPIENT_ID,
                NotificationChannel.SMS_LOCAL, TRANSLATED_MSG,
                NotificationPriority.HIGH, DeliveryStatus.FAILED,
                Instant.now(), null, "SMS gateway timeout", 1);

        when(translationPort.translate(model)).thenReturn(Mono.just(TRANSLATED_MSG));
        when(repository.save(any())).thenReturn(Mono.just(persisted)).thenReturn(Mono.just(failed));
        when(smsProvider.supports(NotificationChannel.SMS_LOCAL)).thenReturn(true);
        when(smsProvider.sendMessage(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("SMS gateway timeout")));
        when(eventPort.publishNotificationFailed(any())).thenReturn(Mono.empty());

        // when / then
        Mono<Notification> result = service.send(
                RECIPIENT_ID, PHONE_NUMBER, model, NotificationChannel.SMS_LOCAL);
        StepVerifier.create(result)
                .expectNextMatches(n -> n.getStatus() == DeliveryStatus.FAILED)
                .verifyComplete();
    }
}
