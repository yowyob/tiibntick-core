package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.model.NotificationTemplateConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Secondary port for managing channel-specific message templates registered
 * on the Kernel notification engine.
 *
 * @author MANFOUO Braun
 */
public interface INotificationTemplateAdminPort {

    Flux<NotificationTemplateConfig> list(String tenantId, String organizationId);

    Mono<NotificationTemplateConfig> save(String tenantId, String organizationId, NotificationTemplateConfig config);
}
