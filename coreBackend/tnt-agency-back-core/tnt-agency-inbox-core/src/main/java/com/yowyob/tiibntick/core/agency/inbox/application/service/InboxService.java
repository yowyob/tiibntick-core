package com.yowyob.tiibntick.core.agency.inbox.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.core.agency.inbox.adapter.in.web.dto.NotificationResponse;
import com.yowyob.tiibntick.core.agency.inbox.adapter.out.persistence.AgencyNotificationR2dbcRepository;
import com.yowyob.tiibntick.core.agency.inbox.application.mapper.InboxMapper;
import com.yowyob.tiibntick.core.agency.inbox.domain.AgencyNotification;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Port of tnt-agency inbox use cases. SSE broadcast and notify-core dispatch stay in the BFF.
 */
@Service
@RequiredArgsConstructor
public class InboxService {

    private final AgencyNotificationR2dbcRepository notificationRepo;
    private final AgencyRegistryService agencyRegistry;

    @Transactional
    public Mono<NotificationResponse> create(CreateInput input) {
        return agencyRegistry.getById(input.tenantId(), input.agencyId())
                .then(Mono.defer(() -> {
                    Instant now = Instant.now();
                    AgencyNotification notification = AgencyNotification.unread(
                            UUID.randomUUID(), input.tenantId(), input.agencyId(),
                            AgencyNotification.mapUiType(input.eventType()), input.eventType(),
                            input.title(), input.body(), input.href(), now);
                    return notificationRepo.save(InboxMapper.toEntity(notification))
                            .map(InboxMapper::toDomain)
                            .map(InboxMapper::toResponse);
                }));
    }

    public Flux<NotificationResponse> listByAgency(UUID tenantId, UUID agencyId, int limit) {
        int capped = Math.min(Math.max(limit, 1), 100);
        return notificationRepo.findByAgency(agencyId, tenantId, capped)
                .map(InboxMapper::toDomain)
                .map(InboxMapper::toResponse);
    }

    @Transactional
    public Mono<NotificationResponse> markRead(UUID tenantId, UUID notificationId) {
        return notificationRepo.findByIdAndTenantId(notificationId, tenantId)
                .map(InboxMapper::toDomain)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "NOTIFICATION_NOT_FOUND", "Notification introuvable: " + notificationId)))
                .flatMap(notification -> {
                    notification.markRead();
                    return notificationRepo.save(InboxMapper.toEntity(notification));
                })
                .map(InboxMapper::toDomain)
                .map(InboxMapper::toResponse);
    }

    @Transactional
    public Mono<Void> markAllRead(UUID tenantId, UUID agencyId) {
        return agencyRegistry.getById(tenantId, agencyId)
                .then(notificationRepo.markAllRead(agencyId, tenantId))
                .then();
    }

    public record CreateInput(
            UUID tenantId, UUID agencyId,
            String eventType, String title, String body, String href) {}
}
