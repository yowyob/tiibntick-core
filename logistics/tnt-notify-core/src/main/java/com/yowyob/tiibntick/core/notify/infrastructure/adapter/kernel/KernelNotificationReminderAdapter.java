package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.yowyob.tiibntick.core.notify.application.port.out.INotificationReminderPort;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationReminder;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.NotificationReminderDto;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.SaveReminderRequestDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implements {@link INotificationReminderPort} via the Kernel notification
 * engine's {@code /api/notifications/reminders} endpoints.
 *
 * @author MANFOUO Braun
 */
public class KernelNotificationReminderAdapter implements INotificationReminderPort {

    private final KernelNotificationClient client;

    public KernelNotificationReminderAdapter(KernelNotificationClient client) {
        this.client = client;
    }

    @Override
    public Flux<NotificationReminder> list(String tenantId, String organizationId) {
        return client.listReminders(tenantId, organizationId).map(this::toDomain);
    }

    @Override
    public Mono<NotificationReminder> save(String tenantId, String organizationId, NotificationReminder reminder) {
        SaveReminderRequestDto request = new SaveReminderRequestDto(
                reminder.templateCode(),
                KernelChannelMapper.toKernel(reminder.channel()),
                parseUuid(reminder.recipientUserId()),
                reminder.recipientAddress(),
                reminder.dueAt(),
                reminder.active(),
                reminder.variables(),
                reminder.metadata());
        return client.saveReminder(tenantId, organizationId, request).map(this::toDomain);
    }

    private NotificationReminder toDomain(NotificationReminderDto dto) {
        return new NotificationReminder(
                dto.id(),
                dto.templateCode(),
                KernelChannelMapper.fromKernel(dto.channel()),
                dto.recipientUserId() != null ? dto.recipientUserId().toString() : null,
                dto.recipientAddress(),
                dto.dueAt(),
                Boolean.TRUE.equals(dto.active()),
                dto.variables(),
                dto.metadata(),
                dto.createdAt(),
                dto.updatedAt());
    }

    private UUID parseUuid(String value) {
        return value != null ? UUID.fromString(value) : null;
    }
}
