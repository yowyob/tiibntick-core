package com.yowyob.tiibntick.core.notify.infrastructure.adapter.profile;

import com.yowyob.tiibntick.core.actor.application.port.out.IProfileWelcomePort;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Welcomes a person whose profile has just been created, through the normal notification process:
 * <ul>
 *   <li>on screen ({@code IN_APP_WEBSOCKET}) with {@code notify.profile.welcome.<kind>};</li>
 *   <li>by e-mail ({@code EMAIL}) with {@code notify.profile.created.<kind>} (subject + body variants).</li>
 * </ul>
 * Both are rendered by {@code NotificationService} in the language of the account, so a client who chose
 * English reads English whatever the language of the platform. Each channel is independent: a failed
 * e-mail never prevents the on-screen notice, and no address means no e-mail.
 */
@Component
public class ProfileWelcomeNotificationAdapter implements IProfileWelcomePort {

    private static final Logger log = LoggerFactory.getLogger(ProfileWelcomeNotificationAdapter.class);

    private final ISendNotificationUseCase notifications;
    private final NotifyProperties properties;

    public ProfileWelcomeNotificationAdapter(ISendNotificationUseCase notifications, NotifyProperties properties) {
        this.notifications = notifications;
        this.properties = properties;
    }

    @Override
    public Mono<Void> welcome(Welcome welcome) {
        String tenant = welcome.tenantId() != null
                ? welcome.tenantId().toString() : properties.getKernel().getDefaultTenantId();
        String organization = properties.getKernel().getDefaultOrganizationId();
        String actor = welcome.actorId().toString();
        // "name" is mandatory in the texts; a person without a known name is greeted without one.
        Map<String, Object> params = Map.of("name", welcome.displayName() != null ? welcome.displayName() : "");

        Mono<Void> onScreen = notifications.send(tenant, organization, actor, actor,
                        model("notify.profile.welcome." + welcome.kind().key(), params), NotificationChannel.IN_APP_WEBSOCKET)
                .then()
                .onErrorResume(e -> {
                    log.warn("On-screen welcome failed for actor {}: {}", actor, e.getMessage());
                    return Mono.empty();
                });

        Mono<Void> email = welcome.email() == null || welcome.email().isBlank()
                ? Mono.empty()
                : notifications.send(tenant, organization, actor, welcome.email(),
                                model("notify.profile.created." + welcome.kind().key(), params), NotificationChannel.EMAIL)
                        .then()
                        .onErrorResume(e -> {
                            log.warn("Welcome e-mail failed for actor {}: {}", actor, e.getMessage());
                            return Mono.empty();
                        });

        return Mono.when(onScreen, email);
    }

    private static NotificationModel model(String key, Map<String, Object> params) {
        // The language is decided per recipient when the notification is rendered; this is only the default.
        return NotificationModel.of(key, "fr", params);
    }
}
