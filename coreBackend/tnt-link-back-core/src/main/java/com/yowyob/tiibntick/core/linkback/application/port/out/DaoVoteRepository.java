package com.yowyob.tiibntick.core.linkback.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Tracks one-vote-per-member enforcement for {@code DaoProposal} voting,
 * kept separate from {@link DaoProposalRepository} since it's a pure
 * membership/idempotency concern, not part of the proposal aggregate itself.
 */
public interface DaoVoteRepository {

    Mono<Boolean> hasVoted(UUID proposalId, UUID voterId);

    Mono<Void> recordVote(UUID proposalId, UUID voterId, boolean inFavor);
}
