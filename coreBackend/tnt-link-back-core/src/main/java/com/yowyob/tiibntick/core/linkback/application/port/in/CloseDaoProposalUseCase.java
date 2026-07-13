package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.domain.model.DaoProposal;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CloseDaoProposalUseCase {
    Mono<DaoProposal> close(UUID tenantId, UUID proposalId);
}
