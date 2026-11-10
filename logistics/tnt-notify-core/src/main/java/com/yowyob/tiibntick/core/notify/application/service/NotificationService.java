package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort.OutboundMessage;
import com.yowyob.tiibntick.core.notify.application.port.out.IPublishNotificationEventPort;
import com.yowyob.tiibntick.core.notify.application.port.out.ITranslationPort;
import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationId;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import reactor.core.publisher.Mono;

/**
 * Hands a business notification over to the Kernel notification engine.
 *
 * <p>TiiBnTick owns no delivery mechanism: no provider, no retry loop, no history table. The service
 * turns the business request into a Kernel delivery (template code + variables, with the i18n text as
 * the required body) and reports the Kernel's answer. A refused or failed hand-over never throws: it
 * is returned as a {@link DeliveryStatus#FAILED} notification and announced on the failure topic, so a
 * notification problem can never break the business flow that triggered it.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public class NotificationService implements ISendNotificationUseCase {

    private final ITranslationPort translationPort;
    private final INotificationDeliveryPort deliveryPort;
    private final IPublishNotificationEventPort eventPort;

    public NotificationService(ITranslationPort translationPort,
            INotificationDeliveryPort deliveryPort,
            IPublishNotificationEventPort eventPort) {
        this.translationPort = translationPort;
        this.deliveryPort = deliveryPort;
        this.eventPort = eventPort;
    }

    @Override
    public Mono<Notification> send(String tenantId,
            String organizationId,
            String recipientId,
            String targetDestination,
            NotificationModel model,
            NotificationChannel channel) {
        return translationPort.translate(model)
                .flatMap(body -> deliveryPort.deliver(new OutboundMessage(
                                channel, tenantId, organizationId, recipientId, targetDestination,
                                model.templateKey(), model.targetLanguage(), model.parameters(), body,
                                model.priority()))
                        .flatMap(receipt -> onAccepted(tenantId, organizationId, recipientId, channel, body,
                                model, receipt))
                        .onErrorResume(e -> onRefused(tenantId, organizationId, recipientId, channel, body,
                                model, e)));
    }

    private Mono<Notification> onAccepted(String tenantId, String organizationId, String recipientId,
            NotificationChannel channel, String body, NotificationModel model,
            INotificationDeliveryPort.DeliveryReceipt receipt) {
        Notification notification = newNotification(receipt.deliveryId(), tenantId, organizationId, recipientId,
                channel, body, model);
        DeliveryStatus status = KernelDeliveryStatus.fromKernel(receipt.status());
        switch (status) {
            case SENT -> notification.markAsSent();
            case FAILED -> notification.markAsFailed("Kernel delivery status " + receipt.status());
            default -> {
                // accepted and queued by the Kernel: it remains the source of truth for the final status
            }
        }
        return switch (status) {
            case SENT -> eventPort.publishNotificationSent(notification).thenReturn(notification);
            case FAILED -> eventPort.publishNotificationFailed(notification).thenReturn(notification);
            default -> Mono.just(notification);
        };
    }

    private Mono<Notification> onRefused(String tenantId, String organizationId, String recipientId,
            NotificationChannel channel, String body, NotificationModel model, Throwable error) {
        Notification notification = newNotification(null, tenantId, organizationId, recipientId, channel, body,
                model);
        notification.markAsFailed(error.getMessage());
        return eventPort.publishNotificationFailed(notification).thenReturn(notification);
    }

    private static Notification newNotification(String deliveryId, String tenantId, String organizationId,
            String recipientId, NotificationChannel channel, String body, NotificationModel model) {
        NotificationId id = deliveryId != null ? new NotificationId(deliveryId) : NotificationId.generate();
        return new Notification(id, tenantId, organizationId, recipientId, channel, body, model.priority());
    }
}
