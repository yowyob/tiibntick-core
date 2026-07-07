package com.yowyob.tiibntick.core.route.application.service;

import com.yowyob.tiibntick.core.geo.domain.model.CostWeights;
import com.yowyob.tiibntick.core.route.application.port.in.IRequestReroutingUseCase;
import com.yowyob.tiibntick.core.route.application.port.out.IRouteEventPublisher;
import com.yowyob.tiibntick.core.route.domain.event.ReroutingTriggeredEvent;
import com.yowyob.tiibntick.core.route.domain.model.ReroutingDecision;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class ReroutingService implements IRequestReroutingUseCase {

    private static final double SWITCHING_COST = 0.05;

    private final AStarPathfinderService pathfinderService;
    private final IRouteEventPublisher eventPublisher;

    public ReroutingService(AStarPathfinderService pathfinderService,
                            IRouteEventPublisher eventPublisher) {
        this.pathfinderService = pathfinderService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Mono<ReroutingDecision> evaluateRerouting(String missionId, String currentNodeId,
                                                      String destinationNodeId, double initialCost,
                                                      UUID tenantId) {
        CostWeights weights = CostWeights.defaultWeights();

        return pathfinderService.findShortestPath(currentNodeId, destinationNodeId, weights, tenantId)
                .map(newPath -> {
                    double newCost = newPath.totalCompositeCost();
                    ReroutingDecision decision = ReroutingDecision.evaluate(
                            missionId, initialCost, newCost, initialCost, SWITCHING_COST);

                    if (decision.shouldReroute()) {
                        eventPublisher.publishReroutingTriggered(
                                ReroutingTriggeredEvent.of(tenantId, missionId,
                                        initialCost, newCost, "cost_improvement"))
                                .subscribe();
                    }
                    return decision;
                })
                .onErrorResume(e -> Mono.just(new ReroutingDecision(
                        missionId, initialCost, initialCost, 0.15 * initialCost,
                        SWITCHING_COST,
                        com.yowyob.tiibntick.core.route.domain.model.ReroutingChoice.KEEP_CURRENT,
                        java.time.Instant.now())));
    }
}
