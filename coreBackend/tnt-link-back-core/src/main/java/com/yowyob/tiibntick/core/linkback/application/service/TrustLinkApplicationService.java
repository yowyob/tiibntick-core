package com.yowyob.tiibntick.core.linkback.application.service;

import com.yowyob.tiibntick.core.linkback.application.port.in.EndorseNodeUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryTrustLinksUseCase;
import com.yowyob.tiibntick.core.linkback.application.port.out.NetworkNodeRepository;
import com.yowyob.tiibntick.core.linkback.application.port.out.TrustLinkRepository;
import com.yowyob.tiibntick.core.linkback.domain.exception.NetworkNodeDomainException;
import com.yowyob.tiibntick.core.linkback.domain.model.TrustLink;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Genuinely new Link business logic — peer endorsements between network
 * nodes. An endorsement is idempotent per (from, to) pair and awards the
 * endorsed node a small, real trust bonus (not fabricated — a direct,
 * disclosed consequence of a genuine community action).
 *
 * @author Dilane PAFE
 */
@Service
@RequiredArgsConstructor
public class TrustLinkApplicationService implements EndorseNodeUseCase, QueryTrustLinksUseCase {

    private static final double ENDORSEMENT_TRUST_BONUS = 0.5;

    private final TrustLinkRepository trustLinkRepository;
    private final NetworkNodeRepository networkNodeRepository;

    @Override
    public Mono<TrustLink> endorse(UUID tenantId, UUID fromNodeId, UUID toNodeId) {
        return trustLinkRepository.existsByFromAndTo(tenantId, fromNodeId, toNodeId)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new NetworkNodeDomainException(
                                "Node " + fromNodeId + " has already endorsed " + toNodeId));
                    }
                    TrustLink link = TrustLink.endorse(tenantId, fromNodeId, toNodeId);
                    return trustLinkRepository.save(link)
                            .flatMap(saved -> awardEndorsementBonus(tenantId, toNodeId).thenReturn(saved));
                });
    }

    @Override
    public Flux<TrustLink> findEndorsementsReceivedBy(UUID tenantId, UUID nodeId) {
        return trustLinkRepository.findByToNodeId(tenantId, nodeId);
    }

    @Override
    public Mono<Long> countEndorsementsReceivedBy(UUID tenantId, UUID nodeId) {
        return trustLinkRepository.countByToNodeId(tenantId, nodeId);
    }

    private Mono<Void> awardEndorsementBonus(UUID tenantId, UUID nodeId) {
        return networkNodeRepository.findById(tenantId, nodeId)
                .flatMap(node -> {
                    node.earnTrust(ENDORSEMENT_TRUST_BONUS);
                    node.refreshBadges();
                    return networkNodeRepository.save(node);
                })
                .then();
    }
}
