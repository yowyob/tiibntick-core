package com.yowyob.tiibntick.core.linkback.adapter.out.persistence;

import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.DaoProposalVoteEntity;
import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository.DaoProposalVoteR2dbcRepository;
import com.yowyob.tiibntick.core.linkback.application.port.out.DaoVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DaoVotePersistenceAdapter implements DaoVoteRepository {

    private final DaoProposalVoteR2dbcRepository r2dbcRepository;

    @Override
    public Mono<Boolean> hasVoted(UUID proposalId, UUID voterId) {
        return r2dbcRepository.existsByProposalIdAndVoterId(proposalId, voterId);
    }

    @Override
    public Mono<Void> recordVote(UUID proposalId, UUID voterId, boolean inFavor) {
        DaoProposalVoteEntity entity = DaoProposalVoteEntity.builder()
                .id(UUID.randomUUID())
                .isNew(true)
                .proposalId(proposalId)
                .voterId(voterId)
                .inFavor(inFavor)
                .votedAt(Instant.now())
                .build();
        return r2dbcRepository.save(entity).then();
    }
}
