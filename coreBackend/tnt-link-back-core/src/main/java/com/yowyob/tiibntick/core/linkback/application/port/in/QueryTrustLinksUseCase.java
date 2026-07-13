package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.domain.model.TrustLink;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface QueryTrustLinksUseCase {

    Flux<TrustLink> findEndorsementsReceivedBy(UUID tenantId, UUID nodeId);

    Mono<Long> countEndorsementsReceivedBy(UUID tenantId, UUID nodeId);
}
