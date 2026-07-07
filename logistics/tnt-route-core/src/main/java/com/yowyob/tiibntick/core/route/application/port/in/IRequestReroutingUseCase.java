package com.yowyob.tiibntick.core.route.application.port.in;

import com.yowyob.tiibntick.core.route.domain.model.ReroutingDecision;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface IRequestReroutingUseCase {
    Mono<ReroutingDecision> evaluateRerouting(String missionId, String currentNodeId,
                                               String destinationNodeId, double initialCost,
                                               UUID tenantId);
}
