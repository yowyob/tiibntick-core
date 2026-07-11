package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;

/**
 * Inbound Port — {@code RecordBadgeUseCase}.
 *
 * <p>Anchors a reputation badge award on Hyperledger Fabric.
 * Makes actor badges portable and verifiable without TiiBnTick's central server.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface RecordBadgeUseCase {

    /**
     * Anchors a badge award on the blockchain.
     *
     * @param actorId   the actor receiving the badge
     * @param badgeType the badge type identifier (e.g., "100_DELIVERIES", "TOP_RATED")
     * @param points    reputation points associated with this badge
     * @param tenantId  the tenant identifier
     * @return a {@link Mono} emitting the Fabric transaction hash
     */
    Mono<String> record(String actorId, String badgeType, int points, String tenantId);
}
