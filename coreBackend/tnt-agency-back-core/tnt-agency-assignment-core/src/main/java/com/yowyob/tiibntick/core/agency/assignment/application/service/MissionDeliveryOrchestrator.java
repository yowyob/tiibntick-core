package com.yowyob.tiibntick.core.agency.assignment.application.service;

import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.assignment.adapter.out.clients.DeliveryCorePort;
import com.yowyob.tiibntick.core.agency.assignment.domain.AgencyMission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MissionDeliveryOrchestrator {

    private final DeliveryCorePort deliveryCore;

    public Mono<AgencyMission> startOnCore(AgencyMission mission, UUID tenantId, Instant now) {
        UUID deliveryId = requireCoreMissionId(mission);
        return deliveryCore.confirmPickup(tenantId, deliveryId)
                .flatMap(v -> deliveryCore.startTransit(tenantId, deliveryId, null, null))
                .doOnNext(view -> DeliveryStatusSyncMapper.applyCoreView(mission, view, now))
                .thenReturn(mission)
                .onErrorResume(e -> {
                    mission.start(now);
                    return Mono.just(mission);
                });
    }

    public Mono<AgencyMission> completeOnCore(
            AgencyMission mission, UUID tenantId, String proofPhotoUrl, Instant now) {
        UUID deliveryId = requireCoreMissionId(mission);
        return deliveryCore.complete(tenantId, deliveryId, proofPhotoUrl)
                .doOnNext(view -> DeliveryStatusSyncMapper.applyCoreView(mission, view, now))
                .thenReturn(mission)
                .onErrorResume(e -> {
                    mission.confirmDelivery(now);
                    return Mono.just(mission);
                });
    }

    public Mono<AgencyMission> cancelOnCore(AgencyMission mission, UUID tenantId, String reason, Instant now) {
        UUID deliveryId = requireCoreMissionId(mission);
        return deliveryCore.cancel(tenantId, deliveryId, reason)
                .then(Mono.fromRunnable(() -> {
                    mission.cancel(reason, now);
                    DeliveryStatusSyncMapper.applyCoreStatus(mission, "CANCELLED", now);
                }))
                .thenReturn(mission)
                .onErrorResume(e -> {
                    mission.cancel(reason, now);
                    return Mono.just(mission);
                });
    }

    public Mono<AgencyMission> failOnCore(AgencyMission mission, UUID tenantId, String reason, Instant now) {
        UUID deliveryId = requireCoreMissionId(mission);
        return deliveryCore.fail(tenantId, deliveryId, reason)
                .doOnNext(view -> DeliveryStatusSyncMapper.applyCoreView(mission, view, now))
                .thenReturn(mission)
                .onErrorResume(e -> {
                    mission.fail(reason, now);
                    return Mono.just(mission);
                });
    }

    public Mono<AgencyMission> depositAtHubOnCore(
            AgencyMission mission, UUID tenantId, UUID coreHubId, Instant now) {
        if (coreHubId == null) {
            return Mono.error(new TntValidationException(
                    "Hub non synchronisé avec Core (coreHubId manquant)."));
        }
        UUID deliveryId = requireCoreMissionId(mission);
        return deliveryCore.depositAtRelay(tenantId, deliveryId, coreHubId)
                .doOnNext(view -> DeliveryStatusSyncMapper.applyCoreView(mission, view, now))
                .thenReturn(mission)
                .onErrorResume(e -> {
                    mission.depositAtHub(now);
                    return Mono.just(mission);
                });
    }

    private static UUID requireCoreMissionId(AgencyMission mission) {
        if (mission.getCoreMissionId() == null) {
            throw new TntValidationException(
                    "Mission sans coreMissionId — impossible de déléguer au Core.");
        }
        return mission.getCoreMissionId();
    }
}
