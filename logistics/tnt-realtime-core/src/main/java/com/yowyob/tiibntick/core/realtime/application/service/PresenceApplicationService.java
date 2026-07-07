package com.yowyob.tiibntick.core.realtime.application.service;

import com.yowyob.tiibntick.core.realtime.application.port.in.IGetPresenceUseCase;
import com.yowyob.tiibntick.core.realtime.application.port.in.IUpdatePresenceUseCase;
import com.yowyob.tiibntick.core.realtime.domain.model.PresenceRecord;
import com.yowyob.tiibntick.core.realtime.domain.model.enums.PresenceStatus;
import com.yowyob.tiibntick.core.realtime.domain.service.PresenceDomainService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Application service for presence management use cases.
 *
 * @author MANFOUO Braun
 */
@Service
public class PresenceApplicationService implements IGetPresenceUseCase, IUpdatePresenceUseCase {

    private final PresenceDomainService presenceDomainService;

    public PresenceApplicationService(PresenceDomainService presenceDomainService) {
        this.presenceDomainService = presenceDomainService;
    }

    @Override
    public Mono<PresenceRecord> getPresence(String userId, String tenantId) {
        return presenceDomainService.getPresence(userId, tenantId);
    }

    @Override
    public Flux<PresenceRecord> getOnlineActors(String tenantId) {
        return presenceDomainService.getOnlineActors(tenantId);
    }

    @Override
    public Mono<Boolean> isOnline(String userId, String tenantId) {
        return presenceDomainService.isOnline(userId, tenantId);
    }

    @Override
    public Mono<Void> updateStatus(String userId, String tenantId, PresenceStatus status) {
        return presenceDomainService.getPresence(userId, tenantId)
                .flatMap(record -> {
                    record.setStatus(status);
                    return presenceDomainService.getPresence(userId, tenantId).then();
                })
                .then();
    }

    @Override
    public Mono<Void> assignMission(String userId, String tenantId, String missionId) {
        return presenceDomainService.assignMission(userId, tenantId, missionId);
    }

    @Override
    public Mono<Void> clearMission(String userId, String tenantId) {
        return presenceDomainService.clearMission(userId, tenantId);
    }
}
