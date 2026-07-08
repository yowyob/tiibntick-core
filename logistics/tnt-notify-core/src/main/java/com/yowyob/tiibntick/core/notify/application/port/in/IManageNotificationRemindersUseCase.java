package com.yowyob.tiibntick.core.notify.application.port.in;

import com.yowyob.tiibntick.core.notify.domain.model.NotificationReminder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Primary port for scheduling and listing future notifications on the
 * Kernel notification engine.
 *
 * @author MANFOUO Braun
 */
public interface IManageNotificationRemindersUseCase {

    Flux<NotificationReminder> listReminders(String tenantId, String organizationId);

    Mono<NotificationReminder> scheduleReminder(String tenantId, String organizationId, NotificationReminder reminder);
}
