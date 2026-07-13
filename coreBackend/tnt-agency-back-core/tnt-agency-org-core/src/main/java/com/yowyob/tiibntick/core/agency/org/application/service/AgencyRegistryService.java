package com.yowyob.tiibntick.core.agency.org.application.service;

import com.yowyob.tiibntick.common.exception.TntConflictException;
import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyRegistryResponse;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencySettingsResponse;
import com.yowyob.tiibntick.core.agency.org.adapter.out.clients.OrganizationCorePort;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRegistryR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencySettingsR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRegistryEntity;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencySettingsEntity;
import com.yowyob.tiibntick.core.agency.org.application.mapper.AgencyOrgMapper;
import com.yowyob.tiibntick.core.agency.eventing.application.port.AgencyEventPublisher;
import com.yowyob.tiibntick.core.agency.eventing.domain.event.AgencyActivated;
import com.yowyob.tiibntick.core.agency.eventing.domain.event.AgencyRegistered;
import com.yowyob.tiibntick.core.agency.eventing.domain.event.AgencySuspended;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgencyRegistryService {

    private static final String STATUS_PENDING = "PENDING_VALIDATION";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_SUSPENDED = "SUSPENDED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final AgencyRegistryR2dbcRepository agencyRepo;
    private final AgencySettingsR2dbcRepository settingsRepo;
    private final OrganizationCorePort organizationCore;
    private final AgencyEventPublisher eventPublisher;

    public Flux<AgencyRegistryResponse> listByTenant(UUID tenantId) {
        return agencyRepo.findByTenantId(tenantId).map(AgencyOrgMapper::toAgencyResponse);
    }

    public Mono<AgencyRegistryResponse> getById(UUID tenantId, UUID agencyId) {
        return requireAgency(agencyId, tenantId).map(AgencyOrgMapper::toAgencyResponse);
    }

    @Transactional
    public Mono<AgencyRegistryResponse> register(RegisterAgencyInput input) {
        return agencyRepo.existsByAgencyCode(input.agencyCode())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(new TntConflictException(
                                "Agency code '" + input.agencyCode() + "' is already in use"));
                    }
                    Instant now = Instant.now();
                    UUID agencyId = UUID.randomUUID();
                    AgencyRegistryEntity agency = AgencyRegistryEntity.builder()
                            .id(agencyId)
                            .tenantId(input.tenantId())
                            .name(input.name())
                            .agencyCode(input.agencyCode())
                            .type(input.type())
                            .status(STATUS_PENDING)
                            .registrationNumber(input.registrationNumber())
                            .addrStreet(input.addrStreet())
                            .addrLandmark(input.addrLandmark())
                            .addrQuarter(input.addrQuarter())
                            .addrCity(input.addrCity())
                            .addrRegion(input.addrRegion())
                            .addrCountry(input.addrCountry())
                            .addrPostalCode(input.addrPostalCode())
                            .addrLat(input.addrLat())
                            .addrLon(input.addrLon())
                            .contactEmail(input.contactEmail())
                            .contactPhone(input.contactPhone())
                            .logoUrl(input.logoUrl())
                            .website(input.website())
                            .createdAt(now)
                            .updatedAt(now)
                            .version(0L)
                            .build();

                    AgencySettingsEntity settings = AgencySettingsEntity.builder()
                            .id(UUID.randomUUID())
                            .tenantId(input.tenantId())
                            .agencyId(agencyId)
                            .autoAssignMissions(false)
                            .allowFreelancerAssociation(false)
                            .hubRetentionDelayHours(72)
                            .defaultCurrency("XAF")
                            .defaultCommissionRate(new BigDecimal("10.00"))
                            .maxActiveBranches(10)
                            .timezone("Africa/Douala")
                            .createdAt(now)
                            .updatedAt(now)
                            .version(0L)
                            .build();

                    return agencyRepo.save(agency)
                            .flatMap(saved -> settingsRepo.save(settings).thenReturn(saved))
                            .flatMap(saved -> eventPublisher.publish(new AgencyRegistered(
                                            UUID.randomUUID(), agencyId, input.tenantId(),
                                            input.name(), input.agencyCode(), input.type(), now))
                                    .thenReturn(saved))
                            .map(AgencyOrgMapper::toAgencyResponse);
                });
    }

    @Transactional
    public Mono<AgencyRegistryResponse> activate(UUID tenantId, UUID agencyId) {
        return requireAgency(agencyId, tenantId)
                .flatMap(agency -> {
                    agency.setStatus(STATUS_ACTIVE);
                    agency.setUpdatedAt(Instant.now());
                    return agencyRepo.save(agency);
                })
                .flatMap(saved -> eventPublisher.publish(new AgencyActivated(
                                UUID.randomUUID(), agencyId, tenantId, null, Instant.now()))
                        .thenReturn(saved))
                .map(AgencyOrgMapper::toAgencyResponse);
    }

    @Transactional
    public Mono<AgencyRegistryResponse> suspend(UUID tenantId, UUID agencyId, String reason) {
        return requireAgency(agencyId, tenantId)
                .flatMap(agency -> {
                    agency.setStatus(STATUS_SUSPENDED);
                    agency.setUpdatedAt(Instant.now());
                    return agencyRepo.save(agency);
                })
                .flatMap(saved -> eventPublisher.publish(new AgencySuspended(
                                UUID.randomUUID(), agencyId, tenantId, reason, null, Instant.now()))
                        .thenReturn(saved))
                .map(AgencyOrgMapper::toAgencyResponse);
    }

    @Transactional
    public Mono<AgencyRegistryResponse> reject(UUID tenantId, UUID agencyId) {
        return requireAgency(agencyId, tenantId)
                .flatMap(agency -> {
                    if (!STATUS_PENDING.equals(agency.getStatus())) {
                        return Mono.error(new IllegalStateException(
                                "Only a PENDING_VALIDATION agency can be rejected (current: "
                                        + agency.getStatus() + ")"));
                    }
                    agency.setStatus(STATUS_REJECTED);
                    agency.setUpdatedAt(Instant.now());
                    return agencyRepo.save(agency);
                })
                .map(AgencyOrgMapper::toAgencyResponse);
    }

    @Transactional
    public Mono<AgencyRegistryResponse> syncPlatformCore(UUID tenantId, UUID agencyId) {
        return requireAgency(agencyId, tenantId)
                .flatMap(agency -> {
                    if (agency.getKernelOrganizationId() == null) {
                        return Mono.error(new TntValidationException(
                                "Platform sync requires kernelOrganizationId."));
                    }
                    if (agency.getCoreAgencyId() != null) {
                        return Mono.just(agency);
                    }
                    return settingsRepo.findByAgencyIdAndTenantId(agencyId, tenantId)
                            .map(AgencySettingsEntity::getDefaultCurrency)
                            .defaultIfEmpty("XAF")
                            .flatMap(currency -> organizationCore.registerAgency(
                                    new OrganizationCorePort.RegisterAgencyRequest(
                                            tenantId,
                                            agency.getKernelOrganizationId(),
                                            agency.getName(),
                                            agency.getRegistrationNumber(),
                                            currency))
                                    .flatMap(coreAgencyId -> {
                                        agency.setCoreAgencyId(coreAgencyId);
                                        agency.setUpdatedAt(Instant.now());
                                        return agencyRepo.save(agency);
                                    }));
                })
                .map(AgencyOrgMapper::toAgencyResponse);
    }

    @Transactional
    public Mono<AgencyRegistryResponse> linkKernelBusinessActor(
            UUID tenantId, UUID agencyId, UUID kernelBusinessActorId) {
        return requireAgency(agencyId, tenantId)
                .flatMap(agency -> {
                    agency.setKernelBusinessActorId(kernelBusinessActorId);
                    agency.setUpdatedAt(Instant.now());
                    return agencyRepo.save(agency);
                })
                .map(AgencyOrgMapper::toAgencyResponse);
    }

    @Transactional
    public Mono<AgencyRegistryResponse> linkKernelIdentity(
            UUID tenantId,
            UUID agencyId,
            UUID kernelOrganizationId,
            UUID kernelBusinessActorId,
            UUID coreAgencyId) {
        return requireAgency(agencyId, tenantId)
                .flatMap(agency -> {
                    agency.setKernelOrganizationId(kernelOrganizationId);
                    agency.setKernelBusinessActorId(kernelBusinessActorId);
                    if (coreAgencyId != null) {
                        agency.setCoreAgencyId(coreAgencyId);
                    }
                    agency.setUpdatedAt(Instant.now());
                    return agencyRepo.save(agency);
                })
                .map(AgencyOrgMapper::toAgencyResponse);
    }

    @Transactional
    public Mono<AgencyRegistryResponse> updateProfile(UpdateProfileInput input) {
        return requireAgency(input.agencyId(), input.tenantId())
                .flatMap(agency -> {
                    if (input.name() != null) agency.setName(input.name());
                    if (input.registrationNumber() != null) agency.setRegistrationNumber(input.registrationNumber());
                    if (input.addrStreet() != null) agency.setAddrStreet(input.addrStreet());
                    if (input.addrLandmark() != null) agency.setAddrLandmark(input.addrLandmark());
                    if (input.addrQuarter() != null) agency.setAddrQuarter(input.addrQuarter());
                    if (input.addrCity() != null) agency.setAddrCity(input.addrCity());
                    if (input.addrRegion() != null) agency.setAddrRegion(input.addrRegion());
                    if (input.addrCountry() != null) agency.setAddrCountry(input.addrCountry());
                    if (input.addrPostalCode() != null) agency.setAddrPostalCode(input.addrPostalCode());
                    if (input.addrLat() != null) agency.setAddrLat(input.addrLat());
                    if (input.addrLon() != null) agency.setAddrLon(input.addrLon());
                    if (input.contactEmail() != null) agency.setContactEmail(input.contactEmail());
                    if (input.contactPhone() != null) agency.setContactPhone(input.contactPhone());
                    if (input.logoUrl() != null) agency.setLogoUrl(input.logoUrl());
                    if (input.website() != null) agency.setWebsite(input.website());
                    agency.setUpdatedAt(Instant.now());
                    return agencyRepo.save(agency);
                })
                .map(AgencyOrgMapper::toAgencyResponse);
    }

    public Mono<AgencySettingsResponse> getSettings(UUID tenantId, UUID agencyId) {
        return settingsRepo.findByAgencyIdAndTenantId(agencyId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "AGENCY_SETTINGS_NOT_FOUND", "Settings not found for agency: " + agencyId)))
                .map(AgencyOrgMapper::toSettingsResponse);
    }

    @Transactional
    public Mono<AgencySettingsResponse> updateSettings(UpdateSettingsInput input) {
        return settingsRepo.findByAgencyIdAndTenantId(input.agencyId(), input.tenantId())
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "AGENCY_SETTINGS_NOT_FOUND", "Settings not found for agency: " + input.agencyId())))
                .flatMap(settings -> {
                    if (input.autoAssignMissions() != null) settings.setAutoAssignMissions(input.autoAssignMissions());
                    if (input.allowFreelancerAssociation() != null) {
                        settings.setAllowFreelancerAssociation(input.allowFreelancerAssociation());
                    }
                    if (input.hubRetentionDelayHours() != null) {
                        settings.setHubRetentionDelayHours(input.hubRetentionDelayHours());
                    }
                    if (input.defaultCommissionRate() != null) {
                        settings.setDefaultCommissionRate(input.defaultCommissionRate());
                    }
                    if (input.maxActiveBranches() != null) settings.setMaxActiveBranches(input.maxActiveBranches());
                    if (input.timezone() != null) settings.setTimezone(input.timezone());
                    settings.setUpdatedAt(Instant.now());
                    return settingsRepo.save(settings);
                })
                .map(AgencyOrgMapper::toSettingsResponse);
    }

    private Mono<AgencyRegistryEntity> requireAgency(UUID agencyId, UUID tenantId) {
        return agencyRepo.findByIdAndTenantId(agencyId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "AGENCY_NOT_FOUND", "Agency not found: " + agencyId)));
    }

    public record RegisterAgencyInput(
            UUID tenantId, String name, String agencyCode, String type,
            String registrationNumber,
            String addrStreet, String addrLandmark, String addrQuarter,
            String addrCity, String addrRegion, String addrCountry, String addrPostalCode,
            Double addrLat, Double addrLon,
            String contactEmail, String contactPhone, String logoUrl, String website) {}

    public record UpdateProfileInput(
            UUID tenantId, UUID agencyId, String name, String registrationNumber,
            String addrStreet, String addrLandmark, String addrQuarter,
            String addrCity, String addrRegion, String addrCountry, String addrPostalCode,
            Double addrLat, Double addrLon,
            String contactEmail, String contactPhone, String logoUrl, String website) {}

    public record UpdateSettingsInput(
            UUID tenantId, UUID agencyId,
            Boolean autoAssignMissions, Boolean allowFreelancerAssociation,
            Integer hubRetentionDelayHours, BigDecimal defaultCommissionRate,
            Integer maxActiveBranches, String timezone) {}
}
