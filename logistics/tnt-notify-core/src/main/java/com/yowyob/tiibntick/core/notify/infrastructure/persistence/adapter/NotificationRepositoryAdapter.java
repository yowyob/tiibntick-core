package com.yowyob.tiibntick.core.notify.infrastructure.persistence.adapter;

import com.yowyob.tiibntick.core.notify.application.port.out.ISearchNotificationsPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationRepositoryPort;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationId;
import com.yowyob.tiibntick.core.notify.infrastructure.persistence.entity.NotificationEntity;
import com.yowyob.tiibntick.core.notify.infrastructure.persistence.repository.NotificationR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Adapter implementing both write (INotificationRepositoryPort) and read
 * (ISearchNotificationsPort) ports.
 * Uses CQRS-lite pattern: same underlying table, split at the port level.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
@Component
public class NotificationRepositoryAdapter implements INotificationRepositoryPort,
        ISearchNotificationsPort {

    private final NotificationR2dbcRepository repository;

    public NotificationRepositoryAdapter(NotificationR2dbcRepository repository) {
        this.repository = repository;
    }

    // ── Write side ────────────────────────────────────────────────────────────

    @Override
    public Mono<Notification> save(Notification notification) {
        return repository.save(toEntity(notification)).map(this::toDomain);
    }

    // ── Read side ─────────────────────────────────────────────────────────────

    @Override
    public Mono<Notification> findById(String notificationId) {
        return repository.findById(notificationId).map(this::toDomain);
    }

    @Override
    public Flux<Notification> findByRecipientId(String recipientId) {
        return repository.findByRecipientId(recipientId).map(this::toDomain);
    }

    @Override
    public Flux<Notification> findByStatus(DeliveryStatus status) {
        return repository.findByStatus(status.name()).map(this::toDomain);
    }

    @Override
    public Flux<Notification> findPendingByRecipient(String recipientId) {
        return repository.findPendingByRecipient(recipientId).map(this::toDomain);
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private NotificationEntity toEntity(Notification n) {
        return NotificationEntity.builder()
                .id(n.getId().value())
                .tenantId(n.getTenantId())
                .organizationId(n.getOrganizationId())
                .recipientId(n.getRecipientId())
                .channel(n.getChannel().name())
                .content(n.getContent())
                .status(n.getStatus().name())
                .priority(n.getPriority().name())
                .attempts(n.getAttempts())
                .createdAt(n.getCreatedAt())
                .sentAt(n.getSentAt())
                .errorMessage(n.getErrorMessage())
                .build();
    }

    private Notification toDomain(NotificationEntity entity) {
        return new Notification(
                new NotificationId(entity.getId()),
                entity.getTenantId(),
                entity.getOrganizationId(),
                entity.getRecipientId(),
                NotificationChannel.valueOf(entity.getChannel()),
                entity.getContent(),
                entity.getPriority() != null
                        ? NotificationPriority.valueOf(entity.getPriority())
                        : NotificationPriority.NORMAL,
                DeliveryStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getSentAt(),
                entity.getErrorMessage(),
                entity.getAttempts() != null ? entity.getAttempts() : 0);
    }
}
