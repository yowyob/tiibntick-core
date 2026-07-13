package com.yowyob.tiibntick.core.agency.assignment.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.core.agency.assignment.adapter.in.web.dto.TrackingResponse;
import com.yowyob.tiibntick.core.agency.assignment.adapter.out.persistence.AgencyMissionR2dbcRepository;
import com.yowyob.tiibntick.core.agency.assignment.application.mapper.MissionMapper;
import com.yowyob.tiibntick.core.agency.assignment.domain.AgencyMission;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRelayHubEntity;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.HubParcelRecordR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.hubops.application.mapper.HubParcelMapper;
import com.yowyob.tiibntick.core.agency.org.hubops.domain.HubParcelRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TrackingService {

    private final HubParcelRecordR2dbcRepository parcelRepo;
    private final AgencyRelayHubR2dbcRepository hubRepo;
    private final AgencyMissionR2dbcRepository missionRepo;

    public Mono<TrackingResponse> trackByCode(UUID tenantId, String trackingCode) {
        String code = normalize(trackingCode);
        return parcelRepo.findByTrackingCodeAndTenantId(code, tenantId)
                .map(HubParcelMapper::toDomain)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "PARCEL_NOT_FOUND", "Colis introuvable pour ce code de suivi")))
                .flatMap(parcel -> enrich(parcel, tenantId));
    }

    public Mono<TrackingResponse> trackByHubAndCode(UUID tenantId, UUID hubId, String trackingCode) {
        String code = normalize(trackingCode);
        return parcelRepo.findByHubIdAndTenantId(hubId, tenantId)
                .map(HubParcelMapper::toDomain)
                .filter(r -> code.equalsIgnoreCase(r.getTrackingCode()))
                .next()
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "PARCEL_NOT_FOUND", "Colis introuvable dans ce hub")))
                .flatMap(parcel -> enrich(parcel, tenantId));
    }

    private Mono<TrackingResponse> enrich(HubParcelRecord parcel, UUID tenantId) {
        return hubRepo.findByIdAndTenantId(parcel.getHubId(), tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "HUB_NOT_FOUND", "Hub relais introuvable")))
                .flatMap(hub -> {
                    UUID missionId = parcel.getMissionId();
                    if (missionId == null) {
                        return Mono.just(toResponse(parcel, hub, null));
                    }
                    return missionRepo.findByIdAndTenantId(missionId, tenantId)
                            .map(MissionMapper::toDomain)
                            .map(mission -> toResponse(parcel, hub, mission))
                            .defaultIfEmpty(toResponse(parcel, hub, null));
                });
    }

    private static TrackingResponse toResponse(HubParcelRecord parcel,
                                               AgencyRelayHubEntity hub,
                                               AgencyMission mission) {
        int capacity = hub.getCapacityUnits() != null ? hub.getCapacityUnits() : 0;
        int occupancy = hub.getCurrentOccupancy() != null ? hub.getCurrentOccupancy() : 0;
        return new TrackingResponse(
                parcel.getId(),
                parcel.getHubId(),
                parcel.getMissionId(),
                parcel.getTrackingCode(),
                parcel.getStatus().name(),
                parcel.getDepositedAt(),
                parcel.getWithdrawalDeadline(),
                parcel.isIdentityVerified(),
                parcel.getWithdrawnBy(),
                parcel.getUpdatedAt(),
                hub.getName(),
                hub.getCode(),
                formatAddress(hub),
                hub.getAddrCity(),
                hub.getOpeningHours(),
                hub.getLatitude(),
                hub.getLongitude(),
                Math.max(0, capacity - occupancy),
                capacity,
                mission != null ? mission.getStatus().name() : null,
                mission != null ? mission.getScheduledAt() : null,
                mission != null ? mission.getStartedAt() : null,
                mission != null ? mission.getCompletedAt() : null
        );
    }

    private static String formatAddress(AgencyRelayHubEntity hub) {
        String joined = Stream.of(hub.getAddrStreet(), hub.getAddrQuarter(), hub.getAddrCity(), hub.getAddrCountry())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
        return joined.isBlank() ? null : joined;
    }

    private static String normalize(String trackingCode) {
        return trackingCode == null ? "" : trackingCode.trim().toUpperCase();
    }
}
