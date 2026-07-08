package com.yowyob.tiibntick.core.notify.application.port.in;

import com.yowyob.tiibntick.core.notify.domain.model.NotificationProviderConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Primary port for administering the Kernel's physical delivery providers
 * (SMTP, Twilio, Meta WhatsApp, Firebase...).
 *
 * @author MANFOUO Braun
 */
public interface IManageNotificationProvidersUseCase {

    Flux<NotificationProviderConfig> listProviders(String tenantId, String organizationId);

    Mono<NotificationProviderConfig> saveProvider(String tenantId, String organizationId,
            NotificationProviderConfig config);
}
