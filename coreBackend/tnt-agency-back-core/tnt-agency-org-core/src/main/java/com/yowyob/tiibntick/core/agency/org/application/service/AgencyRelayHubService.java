package com.yowyob.tiibntick.core.agency.org.application.service;

import com.yowyob.tiibntick.common.exception.TntConflictException;
import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyRelayHubResponse;
import com.yowyob.tiibntick.core.agency.org.adapter.out.clients.OrganizationCorePort;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRegistryR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRegistryEntity;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRelayHubEntity;
import com.yowyob.tiibntick.core.agency.org.application.mapper.AgencyOrgMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgencyRelayHubService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CLOSED = "CLOSED";

    private final AgencyRelayHubR2dbcRepository hubRepo;
    private final AgencyRegistryR2dbcRepository agencyRepo;
    private final OrganizationCorePort organizationCore;

    public Flux<AgencyRelayHubResponse> listByAgency(UUID tenantId, UUID agencyId) {
        return requireAgency(agencyId, tenantId)
                .thenMany(hubRepo.findByAgencyIdAndTenantId(agencyId, tenantId))
                .map(AgencyOrgMapper::toHubResponse);
    }

    public Mono<AgencyRelayHubResponse> getById(UUID tenantId, UUID hubId) {
        return requireHub(hubId, tenantId).map(AgencyOrgMapper::toHubResponse);
    }

    @Transactional
    public Mono<AgencyRelayHubResponse> create(CreateHubInput input) {
        return loadAgency(input.agencyId(), input.tenantId())
                .flatMap(agency -> hubRepo.existsByCode(input.code())
                        .flatMap(exists -> {
                            if (Boolean.TRUE.equals(exists)) {
                                return Mono.error(new TntConflictException(
                                        "Hub code already in use: " + input.code()));
                            }
                            Instant now = Instant.now();
                            AgencyRelayHubEntity hub = AgencyRelayHubEntity.builder()
                                    .id(UUID.randomUUID())
                                    .tenantId(input.tenantId())
                                    .agencyId(input.agencyId())
                                    .branchId(input.branchId())
                                    .name(input.name())
                                    .code(input.code())
                                    .status(STATUS_ACTIVE)
                                    .capacityUnits(input.capacityUnits())
                                    .currentOccupancy(0)
                                    .retentionDelayHours(input.retentionDelayHours())
                                    .openingHours(input.openingHours())
                                    .addrStreet(input.addrStreet())
                                    .addrQuarter(input.addrQuarter())
                                    .addrCity(input.addrCity())
                                    .addrCountry(input.addrCountry())
                                    .latitude(input.latitude())
                                    .longitude(input.longitude())
                                    .createdAt(now)
                                    .updatedAt(now)
                                    .version(0L)
                                    .build();
                            return hubRepo.save(hub)
                                    .flatMap(saved -> syncCoreHub(saved, agency).thenReturn(saved));
                        }))
                .map(AgencyOrgMapper::toHubResponse);
    }

    @Transactional
    public Mono<AgencyRelayHubResponse> configure(ConfigureHubInput input) {
        return requireHub(input.hubId(), input.tenantId())
                .flatMap(hub -> {
                    if (input.name() != null) hub.setName(input.name());
                    if (input.capacityUnits() != null) hub.setCapacityUnits(input.capacityUnits());
                    if (input.retentionDelayHours() != null) {
                        hub.setRetentionDelayHours(input.retentionDelayHours());
                    }
                    if (input.openingHours() != null) hub.setOpeningHours(input.openingHours());
                    hub.setUpdatedAt(Instant.now());
                    return hubRepo.save(hub);
                })
                .map(AgencyOrgMapper::toHubResponse);
    }

    @Transactional
    public Mono<AgencyRelayHubResponse> attachToBranch(UUID tenantId, UUID hubId, UUID branchId) {
        return requireHub(hubId, tenantId)
                .flatMap(hub -> {
                    hub.setBranchId(branchId);
                    hub.setUpdatedAt(Instant.now());
                    return hubRepo.save(hub);
                })
                .map(AgencyOrgMapper::toHubResponse);
    }

    @Transactional
    public Mono<AgencyRelayHubResponse> open(UUID tenantId, UUID hubId) {
        return setStatus(tenantId, hubId, STATUS_OPEN);
    }

    @Transactional
    public Mono<AgencyRelayHubResponse> close(UUID tenantId, UUID hubId) {
        return setStatus(tenantId, hubId, STATUS_CLOSED);
    }

    private Mono<AgencyRelayHubResponse> setStatus(UUID tenantId, UUID hubId, String status) {
        return requireHub(hubId, tenantId)
                .flatMap(hub -> {
                    hub.setStatus(status);
                    hub.setUpdatedAt(Instant.now());
                    return hubRepo.save(hub);
                })
                .map(AgencyOrgMapper::toHubResponse);
    }

    private Mono<AgencyRegistryEntity> loadAgency(UUID agencyId, UUID tenantId) {
        return agencyRepo.findByIdAndTenantId(agencyId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "AGENCY_NOT_FOUND", "Agency not found: " + agencyId)));
    }

    private Mono<Void> requireAgency(UUID agencyId, UUID tenantId) {
        return loadAgency(agencyId, tenantId).then();
    }

    private Mono<AgencyRelayHubEntity> syncCoreHub(AgencyRelayHubEntity hub, AgencyRegistryEntity agency) {
        if (agency.getKernelOrganizationId() == null) {
            return Mono.just(hub);
        }
        if (hub.getLatitude() == null || hub.getLongitude() == null) {
            return Mono.just(hub);
        }
        String wkt = String.format("POINT(%f %f)", hub.getLongitude(), hub.getLatitude());
        return organizationCore.registerHub(new OrganizationCorePort.RegisterHubRequest(
                        agency.getTenantId(),
                        agency.getKernelOrganizationId(),
                        hub.getName(),
                        hub.getCapacityUnits(),
                        wkt,
                        hub.getOpeningHours()))
                .flatMap(coreHubId -> {
                    hub.setCoreHubId(coreHubId);
                    hub.setUpdatedAt(Instant.now());
                    return hubRepo.save(hub);
                });
    }

    private Mono<AgencyRelayHubEntity> requireHub(UUID hubId, UUID tenantId) {
        return hubRepo.findByIdAndTenantId(hubId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "HUB_NOT_FOUND", "Relay hub not found: " + hubId)));
    }

    public record CreateHubInput(
            UUID tenantId, UUID agencyId, UUID branchId,
            String name, String code,
            String addrCity, String addrCountry, String addrStreet, String addrQuarter,
            Double latitude, Double longitude,
            int capacityUnits, int retentionDelayHours, String openingHours) {}

    public record ConfigureHubInput(
            UUID tenantId, UUID hubId,
            String name, Integer capacityUnits, Integer retentionDelayHours, String openingHours) {}
}
