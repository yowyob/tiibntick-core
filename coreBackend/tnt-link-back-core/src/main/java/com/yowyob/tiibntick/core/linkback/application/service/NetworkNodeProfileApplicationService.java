package com.yowyob.tiibntick.core.linkback.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.IFindClientUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindDelivererUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindFreelancerUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindRelayOperatorUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IResolveActorIdentityUseCase;
import com.yowyob.tiibntick.core.actor.domain.model.ActorIdentitySummary;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.GetNetworkNodeProfileUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryDaoZonesUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryTrustLinksUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.result.NetworkNodeProfileResult;
import com.yowyob.tiibntick.core.linkback.application.port.out.NetworkNodeRepository;
import com.yowyob.tiibntick.core.linkback.domain.exception.NetworkNodeDomainException;
import com.yowyob.tiibntick.core.linkback.domain.model.NetworkNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Composes {@link NetworkNode}'s own state with data resolved from
 * tnt-actor-core (display identity, rating) and tnt-delivery-core (delivery
 * counts) plus this module's own DaoZone/TrustLink aggregates — the "rich"
 * node profile the frontend's full {@code NetworkNode} type needs, as opposed
 * to the lightweight {@code /nodes/{id}} lookup used for map rendering.
 *
 * <p>Rating/reviewCount are only meaningful for actor types tnt-actor-core
 * actually rates (Deliverer, Freelancer, RelayOperator) — {@code null} for
 * Client, honestly, not zero-fabricated. Delivery/flow counts are only
 * meaningful for Deliverer/Freelancer (the only types that execute
 * deliveries) — {@code null} otherwise.
 *
 * @author Dilane PAFE
 */
@Service
@RequiredArgsConstructor
public class NetworkNodeProfileApplicationService implements GetNetworkNodeProfileUseCase {

    private final NetworkNodeRepository networkNodeRepository;
    private final IResolveActorIdentityUseCase resolveActorIdentityUseCase;
    private final IFindDelivererUseCase findDelivererUseCase;
    private final IFindFreelancerUseCase findFreelancerUseCase;
    private final IFindRelayOperatorUseCase findRelayOperatorUseCase;
    private final IFindClientUseCase findClientUseCase;
    private final DeliveryQueryUseCase deliveryQueryUseCase;
    private final QueryDaoZonesUseCase queryDaoZonesUseCase;
    private final QueryTrustLinksUseCase queryTrustLinksUseCase;

    @Override
    public Mono<NetworkNodeProfileResult> getProfile(UUID tenantId, UUID nodeId) {
        return networkNodeRepository.findById(tenantId, nodeId)
                .switchIfEmpty(Mono.error(new NetworkNodeDomainException("Network node not found: " + nodeId)))
                .flatMap(node -> Mono.zip(
                        resolveIdentity(node),
                        resolveRating(tenantId, node),
                        resolveDeliveryMetrics(tenantId, node),
                        resolveContainingZoneIds(tenantId, node),
                        queryTrustLinksUseCase.countEndorsementsReceivedBy(tenantId, nodeId)
                ).map(tuple -> new NetworkNodeProfileResult(
                        node,
                        tuple.getT1().displayName(),
                        tuple.getT1().phoneNumber(),
                        tuple.getT1().email(),
                        tuple.getT2().rating(),
                        tuple.getT2().reviewCount(),
                        tuple.getT3().deliveryCount(),
                        tuple.getT3().activeFlows(),
                        tuple.getT4(),
                        tuple.getT5()
                )));
    }

    private Mono<ActorIdentitySummary> resolveIdentity(NetworkNode node) {
        return resolveActorIdentityUseCase.resolve(node.getRefId())
                .defaultIfEmpty(new ActorIdentitySummary(node.getRefId(), null, null, null));
    }

    private Mono<RatingInfo> resolveRating(UUID tenantId, NetworkNode node) {
        Mono<RatingInfo> result = switch (node.getRefType()) {
            case DELIVERER -> findDelivererUseCase.findByActorId(tenantId, node.getRefId())
                    .map(p -> new RatingInfo(p.rating().score(), p.rating().totalRatings()));
            case FREELANCER -> findFreelancerUseCase.findByActorId(tenantId, node.getRefId())
                    .map(p -> new RatingInfo(p.rating().score(), p.rating().totalRatings()));
            case RELAY_OPERATOR -> findRelayOperatorUseCase.findByActorId(tenantId, node.getRefId())
                    .map(p -> new RatingInfo(p.rating().score(), p.rating().totalRatings()));
            case CLIENT -> findClientUseCase.findByActorId(tenantId, node.getRefId())
                    .map(p -> new RatingInfo(p.rating().score(), p.rating().totalRatings()));
            default -> Mono.empty();
        };
        return result.defaultIfEmpty(RatingInfo.NOT_APPLICABLE);
    }

    private Mono<DeliveryMetrics> resolveDeliveryMetrics(UUID tenantId, NetworkNode node) {
        if (node.getRefType() != com.yowyob.tiibntick.core.linkback.domain.model.NodeRefType.DELIVERER
                && node.getRefType() != com.yowyob.tiibntick.core.linkback.domain.model.NodeRefType.FREELANCER) {
            return Mono.just(DeliveryMetrics.NOT_APPLICABLE);
        }
        return Mono.zip(
                deliveryQueryUseCase.countDeliveriesByDeliveryPerson(tenantId, node.getRefId()),
                deliveryQueryUseCase.countNonTerminalDeliveriesByDeliveryPerson(tenantId, node.getRefId())
        ).map(counts -> new DeliveryMetrics(counts.getT1(), counts.getT2()));
    }

    private Mono<List<UUID>> resolveContainingZoneIds(UUID tenantId, NetworkNode node) {
        if (node.getLastKnownLocation() == null) {
            return Mono.just(List.of());
        }
        return queryDaoZonesUseCase.findContaining(tenantId, node.getLastKnownLocation())
                .map(zone -> zone.getId())
                .collectList();
    }

    private record RatingInfo(Double rating, Integer reviewCount) {
        static final RatingInfo NOT_APPLICABLE = new RatingInfo(null, null);
    }

    private record DeliveryMetrics(Long deliveryCount, Long activeFlows) {
        static final DeliveryMetrics NOT_APPLICABLE = new DeliveryMetrics(null, null);
    }
}
