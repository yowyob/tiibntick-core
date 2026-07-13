package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.domain.model.DaoProposal;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface VoteOnDaoProposalUseCase {
    Mono<DaoProposal> vote(UUID tenantId, UUID proposalId, UUID voterId, boolean inFavor);
}
