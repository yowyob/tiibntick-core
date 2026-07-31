package com.yowyob.tiibntick.core.gofreelancer.adapter.out.notification;

import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpNotificationTemplates;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.EmailPort;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Email outbound adapter — delegates to tnt-notify-core / Kernel notification engine.
 * Replaces the former {@code EmailAdapter} (direct JavaMailSender / SMTP).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GofpNotifyEmailAdapter implements EmailPort {

    private static final String FR = SupportedLanguage.FR_CM.getTag();

    private final ISendNotificationUseCase sendNotificationUseCase;
    private final NotifyProperties notifyProperties;

    @Override
    public void sendRegistrationReceived(String to) {
        dispatch(to, GofpNotificationTemplates.REGISTRATION_RECEIVED, Map.of());
    }

    @Override
    public void sendAccountApproved(String to, String loginUrl) {
        dispatch(to, GofpNotificationTemplates.ACCOUNT_APPROVED, Map.of("loginUrl", nullToEmpty(loginUrl)));
    }

    @Override
    public void sendAccountRejected(String to, String reason, String loginUrl) {
        dispatch(to, GofpNotificationTemplates.ACCOUNT_REJECTED,
                Map.of("reason", nullToEmpty(reason), "loginUrl", nullToEmpty(loginUrl)));
    }

    @Override
    public void sendAccountSuspended(String to, String loginUrl) {
        dispatch(to, GofpNotificationTemplates.ACCOUNT_SUSPENDED, Map.of("loginUrl", nullToEmpty(loginUrl)));
    }

    @Override
    public void sendAccountRevoked(String to, String loginUrl) {
        dispatch(to, GofpNotificationTemplates.ACCOUNT_REVOKED, Map.of("loginUrl", nullToEmpty(loginUrl)));
    }

    @Override
    public void sendDeliveryAssigned(String to, String announcementTitle) {
        dispatch(to, GofpNotificationTemplates.DELIVERY_ASSIGNED,
                Map.of("announcementTitle", nullToEmpty(announcementTitle)));
    }

    @Override
    public Mono<Void> sendSimpleMessageReactive(String to, String subject, String text) {
        Map<String, Object> params = Map.of(
                "title", nullToEmpty(subject),
                "body", nullToEmpty(text));
        return send(GofpNotificationTemplates.MESSAGE, params, to, resolveChannel(to))
                .then();
    }

    private void dispatch(String to, String templateKey, Map<String, String> params) {
        Map<String, Object> objectParams = new HashMap<>();
        objectParams.putAll(params);
        send(templateKey, objectParams, to, resolveChannel(to))
                .doOnError(e -> log.warn("Failed to send gofp notification {} to {}: {}",
                        templateKey, to, e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    private Mono<Void> send(String templateKey, Map<String, Object> params, String to, NotificationChannel channel) {
        NotificationModel model = NotificationModel.of(templateKey, FR, params);
        String tenantId = defaultTenantId();
        return sendNotificationUseCase.send(tenantId, null, to, to, model, channel)
                .doOnError(e -> log.warn("Notify-core dispatch failed for {} → {}: {}",
                        templateKey, to, e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private NotificationChannel resolveChannel(String to) {
        return isUuid(to) ? NotificationChannel.IN_APP_WEBSOCKET : NotificationChannel.EMAIL;
    }

    private String defaultTenantId() {
        String configured = notifyProperties.getKernel().getDefaultTenantId();
        return configured != null && !configured.isBlank()
                ? configured
                : TenantContextHolder.SYSTEM_TENANT.toString();
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
