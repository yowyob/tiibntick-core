package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeRepository;
import com.yowyob.tiibntick.core.dispute.application.query.ListDisputesQuery;
import com.yowyob.tiibntick.core.dispute.domain.enums.ActorType;
import com.yowyob.tiibntick.core.dispute.domain.enums.CommentAuthorType;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeEventType;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus;
import com.yowyob.tiibntick.core.dispute.domain.model.*;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity.DisputeCommentEntity;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity.DisputeEscalationEntity;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.entity.DisputeEventEntity;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.persistence.mapper.DisputePersistenceMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DisputeRepositoryAdapter implements IDisputeRepository {

    private static final Logger log = LoggerFactory.getLogger(DisputeRepositoryAdapter.class);

    private final DisputeR2dbcRepository disputeRepo;
    private final DisputeEvidenceR2dbcRepository evidenceRepo;
    private final DisputeEventR2dbcRepository eventRepo;
    private final DisputeCommentR2dbcRepository commentRepo;
    private final DisputeEscalationR2dbcRepository escalationRepo;
    private final ObjectMapper objectMapper;

    public DisputeRepositoryAdapter(
            DisputeR2dbcRepository disputeRepo,
            DisputeEvidenceR2dbcRepository evidenceRepo,
            DisputeEventR2dbcRepository eventRepo,
            DisputeCommentR2dbcRepository commentRepo,
            DisputeEscalationR2dbcRepository escalationRepo,
            ObjectMapper objectMapper) {
        this.disputeRepo = disputeRepo;
        this.evidenceRepo = evidenceRepo;
        this.eventRepo = eventRepo;
        this.commentRepo = commentRepo;
        this.escalationRepo = escalationRepo;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initReferenceSequence() {
        disputeRepo.findMaxReferenceSequence()
                .doOnNext(max -> {
                    DisputeReference.initSequence(max);
                    log.info("Dispute reference sequence initialized to {}", max);
                })
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("Could not initialize dispute reference sequence, starting at 0: {}", e.getMessage());
                    return Mono.empty();
                })
                .block(Duration.ofSeconds(6));
    }

    @Override
    public Mono<Dispute> save(Dispute dispute) {
        return disputeRepo.save(DisputePersistenceMapper.toEntity(dispute))
                .flatMap(saved -> saveChildCollections(dispute))
                .thenReturn(dispute);
    }

    @Override
    public Mono<Dispute> findByIdAndTenantId(DisputeId id, String tenantId) {
        return disputeRepo.findByIdAndTenantId(id.getValue(), tenantId)
                .flatMap(entity -> loadFullDispute(entity.getId(), entity.getTenantId(),
                        DisputePersistenceMapper.toDomain(entity, List.of())));
    }

    @Override
    public Mono<Dispute> findByReferenceAndTenantId(String reference, String tenantId) {
        return disputeRepo.findByReferenceAndTenantId(reference, tenantId)
                .flatMap(entity -> loadFullDispute(entity.getId(), entity.getTenantId(),
                        DisputePersistenceMapper.toDomain(entity, List.of())));
    }

    @Override
    public Flux<Dispute> findAll(ListDisputesQuery query) {
        long offset = (long) query.page() * query.size();
        return disputeRepo.findAllFiltered(
                        query.tenantId(),
                        query.statusFilter() != null ? query.statusFilter().name() : null,
                        query.priorityFilter() != null ? query.priorityFilter().name() : null,
                        query.categoryFilter() != null ? query.categoryFilter().name() : null,
                        query.claimantIdFilter(),
                        query.respondentIdFilter(),
                        query.missionIdFilter(),
                        query.from(),
                        query.to(),
                        query.size(),
                        offset)
                .flatMap(entity -> loadEvidencesOnly(entity.getId(), entity.getTenantId(),
                        DisputePersistenceMapper.toDomain(entity, List.of())));
    }

    @Override
    public Mono<Long> countAll(ListDisputesQuery query) {
        return disputeRepo.countFiltered(
                query.tenantId(),
                query.statusFilter() != null ? query.statusFilter().name() : null,
                query.priorityFilter() != null ? query.priorityFilter().name() : null,
                query.categoryFilter() != null ? query.categoryFilter().name() : null,
                query.claimantIdFilter(),
                query.respondentIdFilter());
    }

    @Override
    public Flux<Dispute> findExpiredByStatusBefore(DisputeStatus status, LocalDateTime before) {
        return disputeRepo.findExpiredByStatusBefore(status.name(), before)
                .flatMap(entity -> loadEvidencesOnly(entity.getId(), entity.getTenantId(),
                        DisputePersistenceMapper.toDomain(entity, List.of())));
    }

    @Override
    public Flux<Dispute> findActiveByClaimantId(String claimantId, String tenantId) {
        return disputeRepo.findByClaimantIdAndTenantIdAndStatusNot(
                        claimantId, tenantId, DisputeStatus.CLOSED_RESOLVED.name())
                .flatMap(entity -> loadEvidencesOnly(entity.getId(), entity.getTenantId(),
                        DisputePersistenceMapper.toDomain(entity, List.of())));
    }

    @Override
    public Flux<Dispute> findByRespondentId(String respondentId, String tenantId) {
        return disputeRepo.findByRespondentIdAndTenantId(respondentId, tenantId)
                .flatMap(entity -> loadEvidencesOnly(entity.getId(), entity.getTenantId(),
                        DisputePersistenceMapper.toDomain(entity, List.of())));
    }

    @Override
    public Mono<Boolean> existsActiveDisputeForPackage(String packageId, String tenantId) {
        return disputeRepo.existsActiveDisputeForPackage(packageId, tenantId);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private Mono<Dispute> loadFullDispute(String disputeId, String tenantId, Dispute base) {
        Mono<List<DisputeEvidence>> evidencesMono = evidenceRepo
                .findByDisputeId(disputeId)
                .map(DisputePersistenceMapper::evidenceToDomain)
                .collectList();

        Mono<List<DisputeEvent>> eventsMono = eventRepo
                .findByDisputeId(disputeId)
                .map(this::toDisputeEvent)
                .collectList();

        Mono<List<DisputeComment>> commentsMono = commentRepo
                .findByDisputeId(disputeId)
                .map(this::toDisputeComment)
                .collectList();

        Mono<List<EscalationRecord>> escalationsMono = escalationRepo
                .findByDisputeId(disputeId)
                .map(this::toEscalationRecord)
                .collectList();

        return Mono.zip(evidencesMono, eventsMono, commentsMono, escalationsMono)
                .map(tuple -> Dispute.reconstituteFull(
                        base.getId(),
                        base.getTenantId(),
                        base.getReference(),
                        base.getCause(),
                        base.getCategory(),
                        base.getPriority(),
                        base.getStatus(),
                        base.getClaimantId(),
                        base.getClaimantType(),
                        base.getRespondentId(),
                        base.getRespondentType(),
                        base.getMissionId(),
                        base.getPackageId(),
                        base.getTrackingCode(),
                        base.getDescription(),
                        base.getFiledAt(),
                        base.getDeadline(),
                        base.getAssignedMediatorId(),
                        base.getResolution(),
                        base.getCompensation(),
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4(),
                        base.getSlaPolicy(),
                        base.getVersion(),
                        base.getRespondentOrgId(),
                        base.getImpliedSubDelivererId(),
                        base.getSubDelivererInvolved()));
    }

    private Mono<Dispute> loadEvidencesOnly(String disputeId, String tenantId, Dispute base) {
        return evidenceRepo.findByDisputeId(disputeId)
                .map(DisputePersistenceMapper::evidenceToDomain)
                .collectList()
                .map(evidences -> Dispute.reconstituteFull(
                        base.getId(),
                        base.getTenantId(),
                        base.getReference(),
                        base.getCause(),
                        base.getCategory(),
                        base.getPriority(),
                        base.getStatus(),
                        base.getClaimantId(),
                        base.getClaimantType(),
                        base.getRespondentId(),
                        base.getRespondentType(),
                        base.getMissionId(),
                        base.getPackageId(),
                        base.getTrackingCode(),
                        base.getDescription(),
                        base.getFiledAt(),
                        base.getDeadline(),
                        base.getAssignedMediatorId(),
                        base.getResolution(),
                        base.getCompensation(),
                        evidences,
                        List.of(),
                        List.of(),
                        List.of(),
                        base.getSlaPolicy(),
                        base.getVersion(),
                        base.getRespondentOrgId(),
                        base.getImpliedSubDelivererId(),
                        base.getSubDelivererInvolved()));
    }

    private Mono<Void> saveChildCollections(Dispute dispute) {
        String disputeId = dispute.getId().getValue();
        String tenantId = dispute.getTenantId();

        Flux<DisputeEscalationEntity> escalationsFlux = Flux.fromIterable(dispute.getEscalationHistory())
                .map(r -> toEscalationEntity(r, disputeId, tenantId));

        return escalationRepo.deleteAll()
                .thenMany(escalationRepo.saveAll(escalationsFlux))
                .then();
    }

    private DisputeEvent toDisputeEvent(DisputeEventEntity e) {
        Map<String, String> metadata = Collections.emptyMap();
        if (e.getMetadataJson() != null) {
            try {
                metadata = objectMapper.readValue(e.getMetadataJson(), new TypeReference<>() {});
            } catch (Exception ignored) {}
        }
        return DisputeEvent.reconstitute(
                DisputeEventId.of(e.getId()),
                DisputeId.of(e.getDisputeId()),
                DisputeEventType.valueOf(e.getType()),
                e.getDescription(),
                e.getPerformedBy(),
                ActorType.valueOf(e.getPerformedByType()),
                e.getOccurredAt(),
                metadata);
    }

    private DisputeComment toDisputeComment(DisputeCommentEntity e) {
        return DisputeComment.reconstitute(
                e.getId(),
                DisputeId.of(e.getDisputeId()),
                e.getAuthorId(),
                CommentAuthorType.valueOf(e.getAuthorType()),
                e.getContent(),
                e.isInternal(),
                e.getPostedAt());
    }

    private EscalationRecord toEscalationRecord(DisputeEscalationEntity e) {
        return EscalationRecord.of(
                e.getEscalatedBy(),
                e.getReason(),
                com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus.valueOf(e.getFromStatus()),
                com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus.valueOf(e.getToStatus()),
                e.getAssignedTo());
    }

    private DisputeEscalationEntity toEscalationEntity(EscalationRecord r, String disputeId, String tenantId) {
        DisputeEscalationEntity e = new DisputeEscalationEntity();
        e.setId(UUID.randomUUID().toString());
        e.setDisputeId(disputeId);
        e.setTenantId(tenantId);
        e.setEscalatedAt(r.getEscalatedAt());
        e.setEscalatedBy(r.getEscalatedBy());
        e.setReason(r.getReason());
        e.setFromStatus(r.getFromStatus().name());
        e.setToStatus(r.getToStatus().name());
        e.setAssignedTo(r.getAssignedTo());
        return e;
    }
    @Override
    public reactor.core.publisher.Flux<com.yowyob.tiibntick.core.dispute.domain.model.Dispute> findByFreelancerOrgId(
            String freelancerOrgId, String status, String tenantId) {
        return disputeRepo.findByRespondentOrgIdAndTenantId(freelancerOrgId, tenantId, status)
                .flatMap(entity -> loadEvidencesOnly(entity.getId(), entity.getTenantId(),
                        DisputePersistenceMapper.toDomain(entity, List.of())));
    }

}