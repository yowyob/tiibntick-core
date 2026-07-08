package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.model.NotificationProviderConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Secondary port for managing physical delivery providers (SMTP, Twilio,
 * Meta WhatsApp, Firebase...) configured on the Kernel notification engine.
 *
 * @author MANFOUO Braun
 */
public interface INotificationProviderAdminPort {

    Flux<NotificationProviderConfig> list(String tenantId, String organizationId);

    Mono<NotificationProviderConfig> save(String tenantId, String organizationId, NotificationProviderConfig config);
}
