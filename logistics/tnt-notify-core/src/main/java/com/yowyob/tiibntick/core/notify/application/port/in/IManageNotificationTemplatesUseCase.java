package com.yowyob.tiibntick.core.notify.application.port.in;

import com.yowyob.tiibntick.core.notify.domain.model.NotificationTemplateConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Primary port for administering channel-specific message templates on the
 * Kernel notification engine.
 *
 * @author MANFOUO Braun
 */
public interface IManageNotificationTemplatesUseCase {

    Flux<NotificationTemplateConfig> listTemplates(String tenantId, String organizationId);

    Mono<NotificationTemplateConfig> saveTemplate(String tenantId, String organizationId,
            NotificationTemplateConfig config);
}
