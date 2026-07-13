package com.yowyob.tiibntick.core.agency.analytics.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.core.agency.assignment.adapter.out.persistence.AgencyMissionR2dbcRepository;
import com.yowyob.tiibntick.core.agency.commission.adapter.out.persistence.CommissionRecordR2dbcRepository;
import com.yowyob.tiibntick.core.agency.fleet.adapter.out.persistence.AgencyVehicleR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyBranchR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.hubops.adapter.out.persistence.HubParcelRecordR2dbcRepository;
import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.DelivererR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only KPI aggregation for agency dashboards and reports.
 * Ported from the BFF analytics services; now the ERP owns KPI computation.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final Set<String> ACTIVE_MISSION_STATUSES = Set.of("PENDING", "ASSIGNED", "IN_TRANSIT");
    private static final String COMMISSION_PENDING = "CALCULATED";
    private static final String UNKNOWN = "UNKNOWN";

    private final AgencyBranchR2dbcRepository branchRepo;
    private final AgencyRelayHubR2dbcRepository hubRepo;
    private final HubParcelRecordR2dbcRepository parcelRepo;
    private final DelivererR2dbcRepository delivererRepo;
    private final AgencyVehicleR2dbcRepository vehicleRepo;
    private final AgencyMissionR2dbcRepository missionRepo;
    private final CommissionRecordR2dbcRepository commissionRepo;

    public Mono<AgencyDashboard> getAgencyDashboard(UUID tenantId, UUID agencyId) {
        Mono<Long> branches = branchRepo.findByAgencyIdAndTenantId(agencyId, tenantId).count();
        Mono<Long> deliverers = delivererRepo.findByAgencyIdAndTenantId(agencyId, tenantId).count();
        Mono<Long> hubs = hubRepo.findByAgencyIdAndTenantId(agencyId, tenantId).count();
        Mono<Long> vehicles = vehicleRepo.findByAgencyIdAndTenantId(agencyId, tenantId).count();
        Mono<Long> activeMissions = missionRepo.findByAgencyIdAndTenantId(agencyId, tenantId)
                .filter(m -> m.getStatus() != null && ACTIVE_MISSION_STATUSES.contains(m.getStatus()))
                .count();
        Mono<Long> pendingCommissions = commissionRepo.findByAgencyIdAndTenantId(agencyId, tenantId)
                .filter(c -> COMMISSION_PENDING.equals(c.getStatus()))
                .count();
        return Mono.zip(branches, deliverers, hubs, vehicles, activeMissions, pendingCommissions)
                .map(t -> new AgencyDashboard(
                        agencyId, t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5(), t.getT6()));
    }

    public Mono<BranchDashboard> getBranchDashboard(UUID tenantId, UUID branchId) {
        return branchRepo.findByIdAndTenantId(branchId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "BRANCH_NOT_FOUND", "Branch not found: " + branchId)))
                .flatMap(branch -> {
                    UUID agencyId = branch.getAgencyId();
                    Mono<Long> deliverers = delivererRepo.findByAgencyIdAndTenantId(agencyId, tenantId)
                            .filter(d -> branchId.equals(d.getBranchId()))
                            .count();
                    Mono<Long> hubs = hubRepo.findByAgencyIdAndTenantId(agencyId, tenantId)
                            .filter(h -> branchId.equals(h.getBranchId()))
                            .count();
                    return Mono.zip(deliverers, hubs)
                            .map(t -> new BranchDashboard(
                                    branchId, agencyId, branch.getName(), branch.getStatus(),
                                    t.getT1(), t.getT2()));
                });
    }

    public Mono<HubReport> getHubReports(UUID tenantId, UUID hubId) {
        return hubRepo.findByIdAndTenantId(hubId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "HUB_NOT_FOUND", "Hub not found: " + hubId)))
                .flatMap(hub -> parcelRepo.findByHubIdAndTenantId(hubId, tenantId)
                        .collectList()
                        .map(parcels -> new HubReport(
                                hubId, hub.getCurrentOccupancy(), hub.getCapacityUnits(),
                                parcels.stream().collect(Collectors.groupingBy(
                                        p -> Optional.ofNullable(p.getStatus()).orElse(UNKNOWN),
                                        Collectors.counting())))));
    }

    public Mono<AgencyReport> getAgencyReports(UUID tenantId, UUID agencyId) {
        Mono<Map<String, Long>> missionsByStatus = missionRepo.findByAgencyIdAndTenantId(agencyId, tenantId)
                .collectList()
                .map(list -> list.stream().collect(Collectors.groupingBy(
                        m -> Optional.ofNullable(m.getStatus()).orElse(UNKNOWN), Collectors.counting())));
        Mono<Map<String, Long>> commissionsByStatus = commissionRepo.findByAgencyIdAndTenantId(agencyId, tenantId)
                .collectList()
                .map(list -> list.stream().collect(Collectors.groupingBy(
                        c -> Optional.ofNullable(c.getStatus()).orElse(UNKNOWN), Collectors.counting())));
        return Mono.zip(missionsByStatus, commissionsByStatus)
                .map(t -> new AgencyReport(agencyId, t.getT1(), t.getT2()));
    }

    public record AgencyDashboard(
            UUID agencyId, long branchesCount, long deliverersCount, long hubsCount,
            long vehiclesCount, long activeMissionsCount, long pendingCommissionsCount) {}

    public record BranchDashboard(
            UUID branchId, UUID agencyId, String branchName, String status,
            long deliverersCount, long hubsCount) {}

    public record HubReport(
            UUID hubId, Integer currentOccupancy, Integer capacityUnits,
            Map<String, Long> parcelsByStatus) {}

    public record AgencyReport(
            UUID agencyId, Map<String, Long> missionsByStatus, Map<String, Long> commissionsByStatus) {}
}
