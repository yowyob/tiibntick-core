package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.application.port.in.result.NetworkNodeProfileResult;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GetNetworkNodeProfileUseCase {
    Mono<NetworkNodeProfileResult> getProfile(UUID tenantId, UUID nodeId);
}
