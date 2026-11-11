package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort.OutboundMessage;
import com.yowyob.tiibntick.core.notify.application.port.out.IPublishNotificationEventPort;
import com.yowyob.tiibntick.core.notify.application.port.out.IRecipientLanguagePort;
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
 * <p><b>Each recipient is rendered separately, in their own language.</b> The language comes from the
 * recipient's account ({@link IRecipientLanguagePort}); the language carried by the {@link NotificationModel}
 * is only a hint for recipients that have none. One business event notifying a client who reads English and
 * a delivery person who reads French therefore produces two messages, each in its recipient's language,
 * from the same model — callers never have to branch on language.
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
    private final IRecipientLanguagePort languagePort;

    /** Without account languages: the language carried by the model is used as is. */
    public NotificationService(ITranslationPort translationPort,
            INotificationDeliveryPort deliveryPort,
            IPublishNotificationEventPort eventPort) {
        this(translationPort, deliveryPort, eventPort, (recipientId, hint) -> Mono.justOrEmpty(hint));
    }

    public NotificationService(ITranslationPort translationPort,
            INotificationDeliveryPort deliveryPort,
            IPublishNotificationEventPort eventPort,
            IRecipientLanguagePort languagePort) {
        this.translationPort = translationPort;
        this.deliveryPort = deliveryPort;
        this.eventPort = eventPort;
        this.languagePort = languagePort;
    }

    @Override
    public Mono<Notification> send(String tenantId,
            String organizationId,
            String recipientId,
            String targetDestination,
            NotificationModel model,
            NotificationChannel channel) {
        return languagePort.languageOf(recipientId, model.targetLanguage())
                .defaultIfEmpty(model.targetLanguage() != null ? model.targetLanguage() : "fr")
                .map(language -> new NotificationModel(model.templateKey(), language, model.parameters(),
                        model.priority()))
                .flatMap(localized -> translationPort.translate(localized, channel)
                        .flatMap(body -> translationPort.translateSubject(localized, channel)
                                .map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty())
                                .flatMap(subject -> deliveryPort.deliver(new OutboundMessage(
                                                channel, tenantId, organizationId, recipientId, targetDestination,
                                                localized.templateKey(), localized.targetLanguage(),
                                                localized.parameters(), body, localized.priority(),
                                                subject.orElse(null)))
                                        .flatMap(receipt -> onAccepted(tenantId, organizationId, recipientId,
                                                channel, body, localized, receipt))
                                        .onErrorResume(e -> onRefused(tenantId, organizationId, recipientId,
                                                channel, body, localized, e)))));
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
