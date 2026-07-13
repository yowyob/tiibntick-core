package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

public interface ActivateBeaconUseCase {

    Mono<NetworkNode> activate(UUID tenantId, UUID nodeId, String message, double radiusKm, Duration duration);

    Mono<NetworkNode> deactivate(UUID tenantId, UUID nodeId);
}
