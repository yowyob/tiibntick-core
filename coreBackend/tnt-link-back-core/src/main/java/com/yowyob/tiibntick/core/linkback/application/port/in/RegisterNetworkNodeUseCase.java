package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.application.port.in.command.RegisterNetworkNodeCommand;
import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;
import reactor.core.publisher.Mono;

public interface RegisterNetworkNodeUseCase {
    Mono<NetworkNode> register(RegisterNetworkNodeCommand command);
}
