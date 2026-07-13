package com.yowyob.tiibntick.core.linkback.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.linkback.adapter.out.persistence.entity.DaoProposalVoteEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DaoProposalVoteR2dbcRepository extends ReactiveCrudRepository<DaoProposalVoteEntity, UUID> {

    Mono<Boolean> existsByProposalIdAndVoterId(UUID proposalId, UUID voterId);
}
