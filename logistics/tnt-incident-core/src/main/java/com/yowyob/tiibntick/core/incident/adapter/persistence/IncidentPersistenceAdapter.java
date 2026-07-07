package com.yowyob.tiibntick.core.incident.adapter.persistence;

import com.yowyob.tiibntick.core.incident.adapter.persistence.repository.*;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentStatus;
import com.yowyob.tiibntick.core.incident.domain.enums.PlatformType;
import com.yowyob.tiibntick.core.incident.domain.model.*;
import com.yowyob.tiibntick.core.incident.port.outbound.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Single persistence adapter implementing all six repository outbound ports using Spring Data R2DBC.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Component
@RequiredArgsConstructor
public class IncidentPersistenceAdapter implements
        IIncidentRepository,
        IIncidentEventLogRepository,
        IIncidentEvidenceRepository,
        IIncidentBlockchainRepository,
        IIncidentDriverReplacementRepository,
        IIncidentCooperationRepository {

    private final IncidentR2dbcRepository incidentRepo;
    private final IncidentEventLogR2dbcRepository eventLogRepo;
    private final IncidentEvidenceR2dbcRepository evidenceRepo;
    private final IncidentBlockchainR2dbcRepository blockchainRepo;
    private final IncidentDriverReplacementR2dbcRepository replacementRepo;
    private final IncidentCooperationR2dbcRepository cooperationRepo;
    private final ParcelIncidentLinkR2dbcRepository linkRepo;
    private final IncidentMapper mapper;

    // ── IIncidentRepository ──────────────────────────────────────────

    @Override
    public Mono<Incident> save(Incident incident) {
        var entity = mapper.toEntity(incident);
        return incidentRepo.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return incidentRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Mono<Incident> findById(UUID id) {
        return incidentRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    public Mono<Incident> findByReferenceCode(String referenceCode) {
        return incidentRepo.findByReferenceCode(referenceCode).map(mapper::toDomain);
    }

    @Override
    public Flux<Incident> findByMissionId(UUID missionId) {
        return incidentRepo.findByMissionId(missionId).map(mapper::toDomain);
    }

    @Override
    public Flux<Incident> findByAgencyIdAndStatus(UUID agencyId, IncidentStatus status) {
        return incidentRepo.findByAgencyIdAndStatus(agencyId, status.name()).map(mapper::toDomain);
    }

    @Override
    public Flux<Incident> findByTenantIdAndStatus(UUID tenantId, IncidentStatus status) {
        return incidentRepo.findByTenantIdAndStatus(tenantId, status.name()).map(mapper::toDomain);
    }

    @Override
    public Flux<Incident> findByAgencyIdAndCreatedBetween(UUID agencyId, Instant from, Instant to) {
        return incidentRepo.findByAgencyIdAndReportedAtBetween(agencyId, from, to).map(mapper::toDomain);
    }

    @Override
    public Flux<Incident> findActiveByPlatform(PlatformType platform, UUID tenantId) {
        return incidentRepo.findActiveByPlatform(tenantId, platform.name()).map(mapper::toDomain);
    }

    @Override
    public Flux<Incident> findByStatusIn(Iterable<IncidentStatus> statuses, UUID tenantId) {
        return Flux.fromIterable(statuses)
                .flatMap(s -> incidentRepo.findByTenantIdAndStatus(tenantId, s.name()))
                .map(mapper::toDomain);
    }

    @Override
    public Flux<Incident> findEscalatedIncidents(UUID tenantId) {
        return incidentRepo.findEscalatedByTenantId(tenantId).map(mapper::toDomain);
    }

    @Override
    public Mono<Long> countActiveByAgency(UUID agencyId) {
        return incidentRepo.countActiveByAgencyId(agencyId);
    }

    // ── IIncidentEventLogRepository ──────────────────────────────────

    @Override
    public Mono<IncidentEventLog> save(IncidentEventLog log) {
        var entity = mapper.toEntity(log);
        return eventLogRepo.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return eventLogRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Flux<IncidentEventLog> findByIncidentIdOrderByOccurredAt(UUID incidentId) {
        return eventLogRepo.findByIncidentIdOrderByOccurredAtAsc(incidentId).map(mapper::toDomain);
    }

    // ── IIncidentEvidenceRepository ──────────────────────────────────

    @Override
    public Mono<IncidentEvidence> save(IncidentEvidence evidence) {
        var entity = mapper.toEntity(evidence);
        return evidenceRepo.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return evidenceRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Flux<IncidentEvidence> findEvidenceByIncidentId(UUID incidentId) {
        return evidenceRepo.findByIncidentId(incidentId).map(mapper::toDomain);
    }

    @Override
    public Mono<IncidentEvidence> findEvidenceById(UUID id) {
        return evidenceRepo.findById(id).map(mapper::toDomain);
    }

    // ── IIncidentBlockchainRepository ────────────────────────────────

    @Override
    public Mono<IncidentBlockchainRecord> save(IncidentBlockchainRecord record) {
        var entity = mapper.toEntity(record);
        return blockchainRepo.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return blockchainRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Flux<IncidentBlockchainRecord> findByChainIdOrderByBlockIndex(String chainId) {
        return blockchainRepo.findByChainIdOrderByBlockIndexAsc(chainId).map(mapper::toDomain);
    }

    @Override
    public Mono<IncidentBlockchainRecord> findLatestByChainId(String chainId) {
        return blockchainRepo.findLatestByChainId(chainId).map(mapper::toDomain);
    }

    @Override
    public Mono<ParcelIncidentLink> saveLink(ParcelIncidentLink link) {
        var entity = mapper.toEntity(link);
        return linkRepo.existsById(entity.getParcelId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return linkRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Flux<ParcelIncidentLink> findLinksByIncidentId(UUID incidentId) {
        return linkRepo.findByIncidentId(incidentId).map(mapper::toDomain);
    }

    @Override
    public Mono<ParcelIncidentLink> findLinkByParcelAndIncident(UUID parcelId, UUID incidentId) {
        return linkRepo.findByParcelIdAndIncidentId(parcelId, incidentId).map(mapper::toDomain);
    }

    // ── IIncidentDriverReplacementRepository ─────────────────────────

    @Override
    public Mono<IncidentDriverReplacement> save(IncidentDriverReplacement replacement) {
        var entity = mapper.toEntity(replacement);
        return replacementRepo.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return replacementRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Mono<IncidentDriverReplacement> findReplacementByIncidentId(UUID incidentId) {
        return replacementRepo.findByIncidentId(incidentId).map(mapper::toDomain);
    }

    // ── IIncidentCooperationRepository ───────────────────────────────

    @Override
    public Mono<IncidentInterAgencyCooperation> save(IncidentInterAgencyCooperation coop) {
        var entity = mapper.toEntity(coop);
        return cooperationRepo.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return cooperationRepo.save(entity);
                })
                .map(mapper::toDomain);
    }

    @Override
    public Flux<IncidentInterAgencyCooperation> findCooperationByIncidentId(UUID incidentId) {
        return cooperationRepo.findByIncidentId(incidentId).map(mapper::toDomain);
    }

    @Override
    public Mono<IncidentInterAgencyCooperation> findCooperationById(UUID id) {
        return cooperationRepo.findById(id).map(mapper::toDomain);
    }

    @Override
    public Flux<IncidentInterAgencyCooperation> findActiveByRespondingAgency(UUID agencyId) {
        return cooperationRepo.findActiveByRespondingAgencyId(agencyId).map(mapper::toDomain);
    }
}
