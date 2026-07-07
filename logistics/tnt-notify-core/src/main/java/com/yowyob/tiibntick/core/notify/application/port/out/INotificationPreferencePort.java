package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.model.NotificationPreference;
import reactor.core.publisher.Mono;

/**
 * Secondary port for user notification preference persistence.
 *
 * @author MANFOUO Braun
 */
public interface INotificationPreferencePort {

    Mono<NotificationPreference> findByUserId(String userId);

    Mono<NotificationPreference> save(NotificationPreference preferences);
}
