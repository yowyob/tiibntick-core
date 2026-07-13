package com.yowyob.tiibntick.core.linkback.adapter.out.persistence;

import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.DaoProposalEntity;
import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository.DaoProposalR2dbcRepository;
import com.yowyob.tiibntick.core.linkback.application.port.out.DaoProposalRepository;
import com.yowyob.tiibntick.core.linkback.domain.model.DaoProposal;
import com.yowyob.tiibntick.core.linkback.domain.model.DaoProposalStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DaoProposalPersistenceAdapter implements DaoProposalRepository {

    private final DaoProposalR2dbcRepository r2dbcRepository;

    @Override
    public Mono<DaoProposal> save(DaoProposal proposal) {
        DaoProposalEntity entity = toEntity(proposal);
        return r2dbcRepository.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return r2dbcRepository.save(entity);
                })
                .map(this::toDomain);
    }

    @Override
    public Mono<DaoProposal> findById(UUID tenantId, UUID proposalId) {
        return r2dbcRepository.findByIdAndTenantId(proposalId, tenantId).map(this::toDomain);
    }

    @Override
    public Flux<DaoProposal> findByZone(UUID tenantId, UUID zoneId) {
        return r2dbcRepository.findByTenantIdAndZoneId(tenantId, zoneId).map(this::toDomain);
    }

    @Override
    public Flux<DaoProposal> findOpenByZone(UUID tenantId, UUID zoneId) {
        return r2dbcRepository.findByTenantIdAndZoneIdAndStatus(tenantId, zoneId, DaoProposalStatus.OPEN.name())
                .map(this::toDomain);
    }

    private DaoProposalEntity toEntity(DaoProposal proposal) {
        return DaoProposalEntity.builder()
                .id(proposal.getId())
                .zoneId(proposal.getZoneId())
                .tenantId(proposal.getTenantId())
                .title(proposal.getTitle())
                .description(proposal.getDescription())
                .proposerId(proposal.getProposerId())
                .status(proposal.getStatus().name())
                .votesFor(proposal.getVotesFor())
                .votesAgainst(proposal.getVotesAgainst())
                .votingDeadline(proposal.getVotingDeadline())
                .createdAt(proposal.getCreatedAt())
                .updatedAt(proposal.getUpdatedAt())
                .build();
    }

    private DaoProposal toDomain(DaoProposalEntity entity) {
        return DaoProposal.builder()
                .id(entity.getId())
                .zoneId(entity.getZoneId())
                .tenantId(entity.getTenantId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .proposerId(entity.getProposerId())
                .status(DaoProposalStatus.valueOf(entity.getStatus()))
                .votesFor(entity.getVotesFor())
                .votesAgainst(entity.getVotesAgainst())
                .votingDeadline(entity.getVotingDeadline())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
