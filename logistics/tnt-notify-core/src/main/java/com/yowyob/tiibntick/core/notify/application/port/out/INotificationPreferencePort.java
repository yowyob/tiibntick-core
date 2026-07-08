package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.model.NotificationPreference;
import reactor.core.publisher.Mono;

/**
 * Secondary port for user notification preference persistence.
 *
 * @author MANFOUO Braun
 */
public interface INotificationPreferencePort {

    Mono<NotificationPreference> findByUserId(String tenantId, String organizationId, String userId);

    /**
     * Saves preferences already carrying their own tenantId/organizationId
     * (see {@link NotificationPreference#getTenantId()}).
     */
    Mono<NotificationPreference> save(NotificationPreference preferences);
}
