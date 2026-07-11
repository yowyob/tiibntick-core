package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.MissionRecord;

/**
 * Inbound Port — {@code RecordMissionUseCase}.
 *
 * <p>Anchors delivery mission lifecycle events on Hyperledger Fabric.
 * Called by {@code tnt-delivery-core} at three mission lifecycle moments:
 * <ol>
 *   <li>Mission created — before delivery starts</li>
 *   <li>Mission completed — all packages delivered</li>
 *   <li>Mission cancelled — delivery aborted</li>
 * </ol>
 *
 * <p>On-chain mission records are the authoritative evidence for:
 * <ul>
 *   <li>Proving a deliverer was assigned a mission</li>
 *   <li>Confirming deliveries for payment processing</li>
 *   <li>Dispute resolution between agencies and deliverers</li>
 * </ul>
 *
 * <p>Implemented by {@link com.yowyob.tiibntick.core.trust.application.service.MissionChainService}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface RecordMissionUseCase {

    /**
     * Anchors a mission creation event on the blockchain.
     * Called immediately after a mission is created and assigned in
     * {@code tnt-delivery-core}.
     *
     * @param missionId    the unique mission identifier
     * @param actorId      the assigned deliverer actor
     * @param tenantId     the tenant identifier
     * @param packageCount the number of packages in the mission
     * @return a {@link Mono} emitting the correlation ID for tracking
     */
    Mono<String> recordCreated(String missionId, String actorId,
                               String tenantId, int packageCount);

    /**
     * Anchors a mission completion event on the blockchain.
     * Called when all packages in the mission have been successfully delivered.
     *
     * @param missionId the mission identifier
     * @param actorId   the deliverer who completed the mission
     * @param tenantId  the tenant identifier
     * @return a {@link Mono} emitting the correlation ID for tracking
     */
    Mono<String> recordCompleted(String missionId, String actorId, String tenantId);

    /**
     * Anchors a mission cancellation event on the blockchain.
     * Called when a mission is cancelled before completion.
     *
     * @param missionId    the mission identifier
     * @param tenantId     the tenant identifier
     * @param cancelReason the reason for cancellation
     * @return a {@link Mono} emitting the correlation ID for tracking
     */
    Mono<String> recordCancelled(String missionId, String tenantId, String cancelReason);

    /**
     * Returns all on-chain records for a given mission, ordered chronologically.
     * Useful for building the complete mission lifecycle audit trail.
     *
     * @param missionId the mission identifier
     * @param tenantId  the tenant identifier
     * @return a {@link Flux} of {@link MissionRecord} instances, oldest first
     */
    Flux<MissionRecord> getMissionHistory(String missionId, String tenantId);
}
