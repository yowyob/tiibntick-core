package com.yowyob.tiibntick.core.notify.application.port.out;

import com.yowyob.tiibntick.core.notify.domain.model.NotificationReminder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Secondary port for scheduling and listing future notifications on the
 * Kernel notification engine.
 *
 * @author MANFOUO Braun
 */
public interface INotificationReminderPort {

    Flux<NotificationReminder> list(String tenantId, String organizationId);

    Mono<NotificationReminder> save(String tenantId, String organizationId, NotificationReminder reminder);
}
