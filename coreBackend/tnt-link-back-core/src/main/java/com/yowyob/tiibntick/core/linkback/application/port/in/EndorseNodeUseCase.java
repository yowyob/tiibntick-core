package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.domain.model.TrustLink;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EndorseNodeUseCase {
    Mono<TrustLink> endorse(UUID tenantId, UUID fromNodeId, UUID toNodeId);
}
