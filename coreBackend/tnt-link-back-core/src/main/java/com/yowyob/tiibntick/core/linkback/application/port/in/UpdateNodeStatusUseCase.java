package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;
import com.yowyob.tiibntick.core.linkback.domain.model.NodeStatus;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UpdateNodeStatusUseCase {
    Mono<NetworkNode> updateStatus(UUID tenantId, UUID nodeId, NodeStatus status);
}
