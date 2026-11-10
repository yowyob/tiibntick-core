package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.yowyob.tiibntick.core.notify.application.port.out.ISearchNotificationsPort;
import com.yowyob.tiibntick.core.notify.application.service.KernelDeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationId;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.dto.NotificationDeliveryDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;

/**
 * Reads the notification history from the Kernel's delivery log. The Kernel offers no per-id or per-user
 * query, so the tenant/organization list is filtered here.
 *
 * @author MANFOUO Braun
 */
public class KernelNotificationSearchAdapter implements ISearchNotificationsPort {

    private final KernelNotificationClient client;

    public KernelNotificationSearchAdapter(KernelNotificationClient client) {
        this.client = client;
    }

    @Override
    public Mono<Notification> findById(String tenantId, String organizationId, String notificationId) {
        return all(tenantId, organizationId)
                .filter(d -> d.id() != null && d.id().toString().equals(notificationId))
                .next()
                .map(this::toNotification);
    }

    @Override
    public Flux<Notification> findByRecipientId(String tenantId, String organizationId, String recipientId) {
        return all(tenantId, organizationId)
                .filter(d -> isRecipient(d, recipientId))
                .map(this::toNotification);
    }

    @Override
    public Flux<Notification> findByStatus(String tenantId, String organizationId, DeliveryStatus status) {
        return all(tenantId, organizationId)
                .filter(d -> KernelDeliveryStatus.fromKernel(d.status()) == status)
                .map(this::toNotification);
    }

    @Override
    public Flux<Notification> findPendingByRecipient(String tenantId, String organizationId, String recipientId) {
        return findByRecipientId(tenantId, organizationId, recipientId)
                .filter(n -> n.getStatus() == DeliveryStatus.PENDING);
    }

    private Flux<NotificationDeliveryDto> all(String tenantId, String organizationId) {
        return client.listDeliveries(tenantId, organizationId);
    }

    private static boolean isRecipient(NotificationDeliveryDto d, String recipientId) {
        return (d.recipientUserId() != null && d.recipientUserId().toString().equals(recipientId))
                || Objects.equals(d.recipientAddress(), recipientId);
    }

    private Notification toNotification(NotificationDeliveryDto d) {
        DeliveryStatus status = KernelDeliveryStatus.fromKernel(d.status());
        NotificationChannel channel = KernelChannelMapper.fromKernel(d.channel());
        return new Notification(
                new NotificationId(d.id() != null ? d.id().toString() : null),
                d.tenantId() != null ? d.tenantId().toString() : null,
                d.organizationId() != null ? d.organizationId().toString() : null,
                d.recipientUserId() != null ? d.recipientUserId().toString() : d.recipientAddress(),
                channel,
                d.body(),
                priorityOf(d),
                status,
                d.requestedAt() != null ? d.requestedAt() : Instant.EPOCH,
                d.sentAt(),
                d.errorMessage(),
                status == DeliveryStatus.FAILED ? 1 : 0);
    }

    private static NotificationPriority priorityOf(NotificationDeliveryDto d) {
        Object p = d.metadata() != null ? d.metadata().get("priority") : null;
        if (p != null) {
            try {
                return NotificationPriority.valueOf(String.valueOf(p));
            } catch (IllegalArgumentException ignored) {
                // unknown value written by another client: fall through to the default
            }
        }
        return NotificationPriority.NORMAL;
    }
}
