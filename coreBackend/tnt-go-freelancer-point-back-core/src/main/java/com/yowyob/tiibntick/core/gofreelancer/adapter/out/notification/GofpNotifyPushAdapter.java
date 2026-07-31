package com.yowyob.tiibntick.core.gofreelancer.adapter.out.notification;

import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpNotificationTemplates;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Push notification adapter — delegates to tnt-notify-core (in-app WebSocket + FCM via Kernel).
 * Replaces the former log-only {@code PushNotificationAdapter}.
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GofpNotifyPushAdapter implements PushNotificationPort {

    private static final String FR = SupportedLanguage.FR_CM.getTag();

    private final ISendNotificationUseCase sendNotificationUseCase;
    private final NotifyProperties notifyProperties;

    @Override
    public Mono<Void> sendPushNotification(UUID userId, String title, String message) {
        String recipientId = userId.toString();
        NotificationModel inAppModel = new NotificationModel(
                GofpNotificationTemplates.MESSAGE, FR,
                Map.of("title", title, "body", message),
                NotificationPriority.NORMAL);
        NotificationModel pushModel = new NotificationModel(
                GofpNotificationTemplates.MESSAGE, FR,
                Map.of("title", title, "body", message),
                NotificationPriority.HIGH);

        String tenantId = defaultTenantId();

        Mono<Void> inApp = sendNotificationUseCase
                .send(tenantId, null, recipientId, recipientId, inAppModel, NotificationChannel.IN_APP_WEBSOCKET)
                .onErrorResume(e -> {
                    log.warn("In-app notification failed for {}: {}", userId, e.getMessage());
                    return Mono.empty();
                })
                .then();

        Mono<Void> push = sendNotificationUseCase
                .send(tenantId, null, recipientId, recipientId, pushModel, NotificationChannel.PUSH_FCM)
                .onErrorResume(e -> {
                    log.warn("Push FCM notification failed for {}: {}", userId, e.getMessage());
                    return Mono.empty();
                })
                .then();

        return Mono.when(inApp, push);
    }

    private String defaultTenantId() {
        String configured = notifyProperties.getKernel().getDefaultTenantId();
        return configured != null && !configured.isBlank()
                ? configured
                : TenantContextHolder.SYSTEM_TENANT.toString();
    }
}
