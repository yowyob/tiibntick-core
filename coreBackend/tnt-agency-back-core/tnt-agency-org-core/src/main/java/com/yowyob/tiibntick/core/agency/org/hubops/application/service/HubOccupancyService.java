package com.yowyob.tiibntick.core.agency.org.hubops.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRelayHubEntity;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.in.web.dto.HubOccupancyResponse;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.clients.InventoryCorePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HubOccupancyService {

    private final AgencyRelayHubR2dbcRepository hubRepo;
    private final InventoryCorePort inventoryCore;

    public Mono<HubOccupancyResponse> getOccupancy(UUID tenantId, UUID hubId) {
        return hubRepo.findByIdAndTenantId(hubId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "HUB_NOT_FOUND", "Hub introuvable: " + hubId)))
                .flatMap(hub -> {
                    if (hub.getCoreHubId() == null) {
                        return Mono.just(fromEntity(hub, false));
                    }
                    return inventoryCore.getOccupancyRate(tenantId, hub.getCoreHubId())
                            .flatMap(rate -> syncOccupancyFromRate(hub, rate).map(saved -> fromEntity(saved, true)))
                            .switchIfEmpty(Mono.just(fromEntity(hub, false)));
                });
    }

    @Transactional
    public Mono<AgencyRelayHubEntity> adjustOccupancy(UUID tenantId, UUID hubId, int delta) {
        return hubRepo.findByIdAndTenantId(hubId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "HUB_NOT_FOUND", "Hub introuvable: " + hubId)))
                .flatMap(hub -> {
                    int capacity = hub.getCapacityUnits() != null ? hub.getCapacityUnits() : 0;
                    int current = hub.getCurrentOccupancy() != null ? hub.getCurrentOccupancy() : 0;
                    int next = Math.max(0, Math.min(capacity, current + delta));
                    hub.setCurrentOccupancy(next);
                    hub.setUpdatedAt(Instant.now());
                    return hubRepo.save(hub);
                });
    }

    @Transactional
    Mono<AgencyRelayHubEntity> syncOccupancyFromRate(AgencyRelayHubEntity hub, double rate) {
        int capacity = hub.getCapacityUnits() != null ? hub.getCapacityUnits() : 0;
        int occupancy = (int) Math.round(capacity * Math.max(0.0, Math.min(1.0, rate)));
        hub.setCurrentOccupancy(Math.min(capacity, occupancy));
        hub.setUpdatedAt(Instant.now());
        return hubRepo.save(hub);
    }

    private static HubOccupancyResponse fromEntity(AgencyRelayHubEntity hub, boolean fromCore) {
        int capacity = hub.getCapacityUnits() != null ? hub.getCapacityUnits() : 0;
        int occupancy = hub.getCurrentOccupancy() != null ? hub.getCurrentOccupancy() : 0;
        int available = Math.max(0, capacity - occupancy);
        return new HubOccupancyResponse(
                hub.getId(), occupancy, capacity, available, available > 0, fromCore);
    }
}
