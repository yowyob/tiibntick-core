package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.domain.model.DaoProposal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface QueryDaoProposalsUseCase {

    Mono<DaoProposal> findById(UUID tenantId, UUID proposalId);

    Flux<DaoProposal> findByZone(UUID tenantId, UUID zoneId);

    Flux<DaoProposal> findOpenByZone(UUID tenantId, UUID zoneId);
}
