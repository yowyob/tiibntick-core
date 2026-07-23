package com.yowyob.tiibntick.core.agency.fleet.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.fleet.adapter.in.web.dto.FleetManLinkResponse;
import com.yowyob.tiibntick.core.agency.fleet.adapter.out.persistence.FleetManLinkR2dbcRepository;
import com.yowyob.tiibntick.core.agency.fleet.adapter.out.persistence.entity.FleetManLinkEntity;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FleetManLinkService {

    private final FleetManLinkR2dbcRepository linkRepo;
    private final AgencyRegistryService agencyRegistry;

    public Mono<FleetManLinkResponse> get(UUID tenantId, UUID agencyId) {
        return agencyRegistry.getById(tenantId, agencyId)
                .then(linkRepo.findByAgencyIdAndTenantId(agencyId, tenantId))
                .map(FleetManLinkService::toResponse)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "FLEETMAN_LINK_NOT_FOUND", "FleetMan link not found for agency: " + agencyId)));
    }

    public Mono<FleetManLinkResponse> findOptional(UUID tenantId, UUID agencyId) {
        return linkRepo.findByAgencyIdAndTenantId(agencyId, tenantId).map(FleetManLinkService::toResponse);
    }

    @Transactional
    public Mono<FleetManLinkResponse> upsert(UUID tenantId, UUID agencyId, UpsertInput input) {
        if (!StringUtils.hasText(input.fleetmanFleetId())) {
            return Mono.error(new TntValidationException("fleetmanFleetId is required"));
        }
        if (!StringUtils.hasText(input.email())) {
            return Mono.error(new TntValidationException("email is required"));
        }
        Instant now = Instant.now();
        return agencyRegistry.getById(tenantId, agencyId)
                .then(linkRepo.findByAgencyIdAndTenantId(agencyId, tenantId))
                .flatMap(existing -> {
                    existing.markNotNew();
                    existing.setFleetmanUserId(input.fleetmanUserId());
                    existing.setFleetmanFleetId(input.fleetmanFleetId());
                    existing.setEmail(input.email());
                    if (input.refreshTokenEnc() != null) {
                        existing.setRefreshTokenEnc(input.refreshTokenEnc());
                    }
                    if (StringUtils.hasText(input.status())) {
                        existing.setStatus(input.status());
                    }
                    existing.setUpdatedAt(now);
                    return linkRepo.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    FleetManLinkEntity created = new FleetManLinkEntity();
                    created.setAgencyId(agencyId);
                    created.setTenantId(tenantId);
                    created.setFleetmanUserId(input.fleetmanUserId());
                    created.setFleetmanFleetId(input.fleetmanFleetId());
                    created.setEmail(input.email());
                    created.setRefreshTokenEnc(input.refreshTokenEnc());
                    created.setStatus(StringUtils.hasText(input.status()) ? input.status() : "ACTIVE");
                    created.setCreatedAt(now);
                    created.setUpdatedAt(now);
                    // isNewEntity defaults to true → INSERT
                    return linkRepo.save(created);
                }))
                .map(FleetManLinkService::toResponse);
    }

    private static FleetManLinkResponse toResponse(FleetManLinkEntity e) {
        return new FleetManLinkResponse(
                e.getAgencyId(), e.getTenantId(), e.getFleetmanUserId(), e.getFleetmanFleetId(),
                e.getEmail(), e.getRefreshTokenEnc(), e.getStatus(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    public record UpsertInput(
            String fleetmanUserId,
            String fleetmanFleetId,
            String email,
            String refreshTokenEnc,
            String status) {}
}
