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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads the notification history from the Kernel's delivery log. The Kernel offers no per-id or per-user
 * query, so the tenant/organization list is filtered here.
 *
 * <p>That list is the whole log of the tenant, and every signed-in user polls their own history, so it is
 * fetched once per tenant/organization and shared for a few seconds ({@link #DEFAULT_LOG_TTL}): concurrent and
 * repeated reads cost one Kernel call, not one each. The Kernel reads are fail-open, so a Kernel hiccup shows
 * as an empty history for at most that long.
 *
 * @author MANFOUO Braun
 */
public class KernelNotificationSearchAdapter implements ISearchNotificationsPort {

    /** How long a fetched delivery log is shared between readers. */
    static final Duration DEFAULT_LOG_TTL = Duration.ofSeconds(10);

    private final KernelNotificationClient client;
    private final Duration logTtl;
    /** One cached read per tenant/organization; the Mono itself re-fetches once its TTL has elapsed. */
    private final ConcurrentHashMap<String, Mono<List<NotificationDeliveryDto>>> logs = new ConcurrentHashMap<>();

    public KernelNotificationSearchAdapter(KernelNotificationClient client) {
        this(client, DEFAULT_LOG_TTL);
    }

    KernelNotificationSearchAdapter(KernelNotificationClient client, Duration logTtl) {
        this.client = client;
        this.logTtl = logTtl;
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
        if (logTtl.isZero() || logTtl.isNegative()) {
            return client.listDeliveries(tenantId, organizationId);
        }
        String key = tenantId + "|" + organizationId;
        return logs.computeIfAbsent(key, k -> client.listDeliveries(tenantId, organizationId)
                        .collectList()
                        .cache(list -> logTtl, error -> Duration.ZERO, () -> logTtl))
                .flatMapMany(Flux::fromIterable);
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
