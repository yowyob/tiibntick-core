package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.domain.model.NetworkAlert;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ConfirmNetworkAlertUseCase {
    Mono<NetworkAlert> confirm(UUID tenantId, UUID alertId);
}
