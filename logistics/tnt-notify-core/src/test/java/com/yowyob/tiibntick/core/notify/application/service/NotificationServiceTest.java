package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort.DeliveryReceipt;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort.OutboundMessage;
import com.yowyob.tiibntick.core.notify.application.port.out.IPublishNotificationEventPort;
import com.yowyob.tiibntick.core.notify.application.port.out.ITranslationPort;
import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationService}: the hand-over of a business notification to the Kernel.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private ITranslationPort translationPort;
    @Mock private INotificationDeliveryPort deliveryPort;
    @Mock private IPublishNotificationEventPort eventPort;

    private NotificationService service;

    private static final String TENANT_ID = "5b1f6e2a-0000-4c3a-9a2a-000000000001";
    private static final String ORGANIZATION_ID = "5b1f6e2a-0000-4c3a-9a2a-000000000002";
    private static final String RECIPIENT_ID = "5b1f6e2a-0000-4c3a-9a2a-000000000003";
    private static final String DELIVERY_ID = "5b1f6e2a-0000-4c3a-9a2a-0000000000d1";
    private static final String TEXT = "Package PKG-99 has been delivered.";

    private final NotificationModel model = new NotificationModel(
            "notification.package.delivered", "en_CM", Map.of("package_id", "PKG-99"), NotificationPriority.HIGH);

    @BeforeEach
    void setUp() {
        service = new NotificationService(translationPort, deliveryPort, eventPort);
        when(translationPort.translate(any(NotificationModel.class), any(NotificationChannel.class)))
                .thenReturn(Mono.just(TEXT));
        when(translationPort.translateSubject(any(NotificationModel.class), any(NotificationChannel.class)))
                .thenReturn(Mono.empty());
    }

    @Test
    @DisplayName("hands the template, variables, locale and the translated body to the Kernel, and reports SENT")
    void sentByKernel() {
        when(deliveryPort.deliver(any())).thenReturn(Mono.just(new DeliveryReceipt(DELIVERY_ID, "SENT")));
        when(eventPort.publishNotificationSent(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.send(TENANT_ID, ORGANIZATION_ID, RECIPIENT_ID, "+237691000001", model,
                        NotificationChannel.SMS_LOCAL))
                .assertNext(n -> {
                    assertThat(n.getStatus()).isEqualTo(DeliveryStatus.SENT);
                    assertThat(n.getId().value()).isEqualTo(DELIVERY_ID);
                    assertThat(n.getContent()).isEqualTo(TEXT);
                })
                .verifyComplete();

        ArgumentCaptor<OutboundMessage> sent = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(deliveryPort).deliver(sent.capture());
        OutboundMessage m = sent.getValue();
        assertThat(m.channel()).isEqualTo(NotificationChannel.SMS_LOCAL);
        assertThat(m.tenantId()).isEqualTo(TENANT_ID);
        assertThat(m.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(m.recipientId()).isEqualTo(RECIPIENT_ID);
        assertThat(m.destination()).isEqualTo("+237691000001");
        assertThat(m.templateCode()).isEqualTo("notification.package.delivered");
        assertThat(m.locale()).isEqualTo("en_CM");
        assertThat(m.variables()).containsEntry("package_id", "PKG-99");
        assertThat(m.body()).isEqualTo(TEXT);
        assertThat(m.priority()).isEqualTo(NotificationPriority.HIGH);
    }

    @Test
    @DisplayName("a delivery the Kernel only queued stays PENDING: the Kernel owns the final status, no event yet")
    void queuedByKernel() {
        when(deliveryPort.deliver(any())).thenReturn(Mono.just(new DeliveryReceipt(DELIVERY_ID, "QUEUED")));

        StepVerifier.create(service.send(TENANT_ID, ORGANIZATION_ID, RECIPIENT_ID, RECIPIENT_ID, model,
                        NotificationChannel.IN_APP_WEBSOCKET))
                .assertNext(n -> assertThat(n.getStatus()).isEqualTo(DeliveryStatus.PENDING))
                .verifyComplete();

        verify(eventPort, never()).publishNotificationSent(any());
        verify(eventPort, never()).publishNotificationFailed(any());
    }

    @Test
    @DisplayName("a delivery the Kernel reports FAILED is returned FAILED and announced on the failure topic")
    void failedByKernel() {
        when(deliveryPort.deliver(any())).thenReturn(Mono.just(new DeliveryReceipt(DELIVERY_ID, "FAILED")));
        when(eventPort.publishNotificationFailed(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.send(TENANT_ID, ORGANIZATION_ID, RECIPIENT_ID, "user@example.com", model,
                        NotificationChannel.EMAIL))
                .assertNext(n -> assertThat(n.getStatus()).isEqualTo(DeliveryStatus.FAILED))
                .verifyComplete();
        verify(eventPort).publishNotificationFailed(any());
    }

    @Test
    @DisplayName("a refused hand-over (Kernel down, no organization…) never throws: FAILED + failure event")
    void refusedHandOverDoesNotBreakTheCaller() {
        when(deliveryPort.deliver(any())).thenReturn(Mono.error(new IllegalStateException("kernel unreachable")));
        when(eventPort.publishNotificationFailed(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.send(TENANT_ID, null, RECIPIENT_ID, "+237691000001", model,
                        NotificationChannel.SMS_LOCAL))
                .assertNext(n -> {
                    assertThat(n.getStatus()).isEqualTo(DeliveryStatus.FAILED);
                    assertThat(n.getErrorMessage()).contains("kernel unreachable");
                })
                .verifyComplete();
        verify(eventPort).publishNotificationFailed(any());
    }
}
