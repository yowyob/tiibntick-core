package com.yowyob.tiibntick.core.agency.org.application.service;

import com.yowyob.tiibntick.common.exception.TntConflictException;
import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyBranchResponse;
import com.yowyob.tiibntick.core.agency.org.adapter.out.clients.OrganizationCorePort;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyBranchR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRegistryR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyBranchEntity;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRegistryEntity;
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
public class AgencyBranchService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CLOSED = "CLOSED";

    private final AgencyBranchR2dbcRepository branchRepo;
    private final AgencyRegistryR2dbcRepository agencyRepo;
    private final OrganizationCorePort organizationCore;

    public Flux<AgencyBranchResponse> listByAgency(UUID tenantId, UUID agencyId) {
        return requireAgency(agencyId, tenantId)
                .thenMany(branchRepo.findByAgencyIdAndTenantId(agencyId, tenantId))
                .map(AgencyOrgMapper::toBranchResponse);
    }

    public Mono<AgencyBranchResponse> getById(UUID tenantId, UUID branchId) {
        return requireBranch(branchId, tenantId).map(AgencyOrgMapper::toBranchResponse);
    }

    @Transactional
    public Mono<AgencyBranchResponse> create(CreateBranchInput input) {
        return loadAgency(input.agencyId(), input.tenantId())
                .flatMap(agency -> branchRepo.existsByCode(input.code())
                        .flatMap(exists -> {
                            if (Boolean.TRUE.equals(exists)) {
                                return Mono.error(new TntConflictException(
                                        "Branch code already in use: " + input.code()));
                            }
                            Instant now = Instant.now();
                            AgencyBranchEntity branch = AgencyBranchEntity.builder()
                                    .id(UUID.randomUUID())
                                    .tenantId(input.tenantId())
                                    .agencyId(input.agencyId())
                                    .name(input.name())
                                    .code(input.code())
                                    .status(STATUS_OPEN)
                                    .addrStreet(input.addrStreet())
                                    .addrLandmark(input.addrLandmark())
                                    .addrQuarter(input.addrQuarter())
                                    .addrCity(input.addrCity())
                                    .addrRegion(input.addrRegion())
                                    .addrCountry(input.addrCountry())
                                    .addrPostalCode(input.addrPostalCode())
                                    .addrLat(input.addrLat())
                                    .addrLon(input.addrLon())
                                    .createdAt(now)
                                    .updatedAt(now)
                                    .version(0L)
                                    .build();
                            return branchRepo.save(branch)
                                    .flatMap(saved -> syncCoreBranch(saved, agency).thenReturn(saved));
                        }))
                .map(AgencyOrgMapper::toBranchResponse);
    }

    @Transactional
    public Mono<AgencyBranchResponse> update(UpdateBranchInput input) {
        return requireBranch(input.branchId(), input.tenantId())
                .flatMap(branch -> {
                    branch.setName(input.name());
                    if (input.addrStreet() != null) branch.setAddrStreet(input.addrStreet());
                    if (input.addrLandmark() != null) branch.setAddrLandmark(input.addrLandmark());
                    if (input.addrQuarter() != null) branch.setAddrQuarter(input.addrQuarter());
                    if (input.addrCity() != null) branch.setAddrCity(input.addrCity());
                    if (input.addrRegion() != null) branch.setAddrRegion(input.addrRegion());
                    if (input.addrCountry() != null) branch.setAddrCountry(input.addrCountry());
                    if (input.addrPostalCode() != null) branch.setAddrPostalCode(input.addrPostalCode());
                    if (input.addrLat() != null) branch.setAddrLat(input.addrLat());
                    if (input.addrLon() != null) branch.setAddrLon(input.addrLon());
                    branch.setUpdatedAt(Instant.now());
                    return branchRepo.save(branch);
                })
                .map(AgencyOrgMapper::toBranchResponse);
    }

    @Transactional
    public Mono<AgencyBranchResponse> assignManager(UUID tenantId, UUID branchId, UUID managerId) {
        return requireBranch(branchId, tenantId)
                .flatMap(branch -> {
                    branch.setManagerId(managerId);
                    branch.setUpdatedAt(Instant.now());
                    return branchRepo.save(branch);
                })
                .map(AgencyOrgMapper::toBranchResponse);
    }

    @Transactional
    public Mono<AgencyBranchResponse> clearManager(UUID tenantId, UUID branchId) {
        return requireBranch(branchId, tenantId)
                .flatMap(branch -> {
                    branch.setManagerId(null);
                    branch.setUpdatedAt(Instant.now());
                    return branchRepo.save(branch);
                })
                .map(AgencyOrgMapper::toBranchResponse);
    }

    @Transactional
    public Mono<AgencyBranchResponse> changeStatus(UUID tenantId, UUID branchId, String status) {
        return requireBranch(branchId, tenantId)
                .flatMap(branch -> {
                    branch.setStatus(status);
                    branch.setUpdatedAt(Instant.now());
                    return branchRepo.save(branch);
                })
                .map(AgencyOrgMapper::toBranchResponse);
    }

    @Transactional
    public Mono<Void> close(UUID tenantId, UUID branchId) {
        return changeStatus(tenantId, branchId, STATUS_CLOSED).then();
    }

    private Mono<AgencyRegistryEntity> loadAgency(UUID agencyId, UUID tenantId) {
        return agencyRepo.findByIdAndTenantId(agencyId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "AGENCY_NOT_FOUND", "Agency not found: " + agencyId)));
    }

    private Mono<Void> requireAgency(UUID agencyId, UUID tenantId) {
        return loadAgency(agencyId, tenantId).then();
    }

    private Mono<AgencyBranchEntity> syncCoreBranch(AgencyBranchEntity branch, AgencyRegistryEntity agency) {
        if (agency.getKernelOrganizationId() == null) {
            return Mono.just(branch);
        }
        if (agency.getCoreAgencyId() == null) {
            return Mono.error(new TntValidationException(
                    "Branch platform sync requires coreAgencyId — sync agency first."));
        }
        return organizationCore.registerBranch(new OrganizationCorePort.RegisterBranchRequest(
                        agency.getTenantId(),
                        agency.getCoreAgencyId(),
                        agency.getKernelOrganizationId(),
                        branch.getName(),
                        formatAddress(branch)))
                .flatMap(coreBranchId -> {
                    branch.setCoreBranchId(coreBranchId);
                    branch.setUpdatedAt(Instant.now());
                    return branchRepo.save(branch);
                });
    }

    private static String formatAddress(AgencyBranchEntity branch) {
        StringBuilder sb = new StringBuilder();
        if (branch.getAddrStreet() != null && !branch.getAddrStreet().isBlank()) {
            sb.append(branch.getAddrStreet()).append(", ");
        }
        if (branch.getAddrQuarter() != null && !branch.getAddrQuarter().isBlank()) {
            sb.append(branch.getAddrQuarter()).append(", ");
        }
        if (branch.getAddrCity() != null) {
            sb.append(branch.getAddrCity());
        }
        if (branch.getAddrCountry() != null) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(branch.getAddrCountry());
        }
        return sb.toString();
    }

    private Mono<AgencyBranchEntity> requireBranch(UUID branchId, UUID tenantId) {
        return branchRepo.findByIdAndTenantId(branchId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "BRANCH_NOT_FOUND", "Branch not found: " + branchId)));
    }

    public record CreateBranchInput(
            UUID tenantId, UUID agencyId, String name, String code,
            String addrStreet, String addrLandmark, String addrQuarter,
            String addrCity, String addrRegion, String addrCountry, String addrPostalCode,
            Double addrLat, Double addrLon) {}

    public record UpdateBranchInput(
            UUID tenantId, UUID branchId, String name,
            String addrStreet, String addrLandmark, String addrQuarter,
            String addrCity, String addrRegion, String addrCountry, String addrPostalCode,
            Double addrLat, Double addrLon) {}
}
