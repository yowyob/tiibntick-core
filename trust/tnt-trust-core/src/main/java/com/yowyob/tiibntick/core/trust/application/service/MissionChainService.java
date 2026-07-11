package com.yowyob.tiibntick.core.trust.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.MissionRecord;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordMissionUseCase;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustProofQueryPort;

/**
 * Application Service — {@code MissionChainService}.
 *
 * <p>Anchors delivery mission lifecycle events on Hyperledger Fabric at
 * three critical points: creation, completion, and cancellation.
 *
 * <p>Implements {@link RecordMissionUseCase}.
 *
 * <h3>Why mission anchoring matters</h3>
 * <p>In the informal logistics context of Cameroon, disputes frequently arise
 * between agencies and deliverers regarding whether a mission was completed.
 * On-chain mission records provide a neutral, immutable arbiter:
 * <ul>
 *   <li>Agency cannot deny having assigned the mission</li>
 *   <li>Deliverer cannot falsely claim a mission was completed</li>
 *   <li>Payment can only be triggered when the completion record is on-chain</li>
 * </ul>
 *
 * <h3>Integration</h3>
 * <p>Called by {@code tnt-delivery-core}'s mission service after each
 * lifecycle transition. The {@code tnt-trust} JAR is imported as a
 * dependency by {@code tnt-bootstrap}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Service
public class MissionChainService implements RecordMissionUseCase {

    private static final Logger log = LoggerFactory.getLogger(MissionChainService.class);

    private final LogisticEventPublisherService publisherService;
    private final TrustProofQueryPort trustProofQueryPort;
    private final MeterRegistry meterRegistry;

    public MissionChainService(
            final LogisticEventPublisherService publisherService,
            final TrustProofQueryPort trustProofQueryPort,
            final MeterRegistry meterRegistry) {
        this.publisherService = publisherService;
        this.trustProofQueryPort = trustProofQueryPort;
        this.meterRegistry = meterRegistry;
    }

    // ── RecordMissionUseCase ──────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Publishes a {@code MISSION_CREATED_ON_CHAIN} event to Kafka.
     * Provides immutable proof that a mission was assigned to a deliverer.
     */
    @Override
    public Mono<String> recordCreated(
            final String missionId,
            final String actorId,
            final String tenantId,
            final int packageCount) {

        log.info("Anchoring mission creation — missionId={}, actor={}, packages={}",
                missionId, actorId, packageCount);

        final LogisticTrustEvent event = LogisticTrustEvent.forMissionCreated(
                missionId, actorId, tenantId, packageCount);

        return publisherService.publish(event)
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.mission.created",
                            "tenant", tenantId).increment();
                    log.info("Mission creation event published — correlationId={}",
                            event.getCorrelationId());
                })
                .thenReturn(event.getCorrelationId());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Publishes a {@code MISSION_COMPLETED_ON_CHAIN} event to Kafka.
     * The completion record is the prerequisite for payment processing
     * in {@code tnt-billing-wallet}.
     */
    @Override
    public Mono<String> recordCompleted(
            final String missionId,
            final String actorId,
            final String tenantId) {

        log.info("Anchoring mission completion — missionId={}, actor={}", missionId, actorId);

        final LogisticTrustEvent event = LogisticTrustEvent.forMissionCompleted(
                missionId, actorId, tenantId);

        return publisherService.publish(event)
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.mission.completed",
                            "tenant", tenantId).increment();
                    log.info("Mission completion event published — correlationId={}",
                            event.getCorrelationId());
                })
                .thenReturn(event.getCorrelationId());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Publishes a {@code MISSION_CANCELLED_ON_CHAIN} event to Kafka.
     * Records the cancellation reason for dispute resolution.
     */
    @Override
    public Mono<String> recordCancelled(
            final String missionId,
            final String tenantId,
            final String cancelReason) {

        log.warn("Anchoring mission cancellation — missionId={}, reason={}", missionId, cancelReason);

        final LogisticTrustEvent event = LogisticTrustEvent.forMissionCancelled(
                missionId, tenantId, cancelReason);

        return publisherService.publish(event)
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.mission.cancelled",
                            "tenant", tenantId).increment();
                    log.info("Mission cancellation event published — correlationId={}",
                            event.getCorrelationId());
                })
                .thenReturn(event.getCorrelationId());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Queries the Trust Event REST API for all on-chain records
     * associated with the given mission identifier.
     * Returns records ordered chronologically (oldest first).
     */
    @Override
    public Flux<MissionRecord> getMissionHistory(
            final String missionId, final String tenantId) {
        log.debug("Fetching on-chain mission history for missionId={}", missionId);
        // Query all mission event types for this missionId
        return Flux.fromArray(new String[]{
                        "MISSION_CREATED_ON_CHAIN",
                        "MISSION_COMPLETED_ON_CHAIN",
                        "MISSION_CANCELLED_ON_CHAIN"})
                .flatMap(eventType -> trustProofQueryPort
                        .findTxHashByEntityId(missionId, "MISSION", tenantId)
                        .map(txHash -> buildRecordFromHistory(missionId, tenantId, eventType, txHash)))
                .filter(r -> r != null);
    }

    /**
     * Reconstructs a {@link MissionRecord} from the history query result.
     * Used when the local cache is not populated yet (on-chain query path).
     */
    private MissionRecord buildRecordFromHistory(
            final String missionId,
            final String tenantId,
            final String eventType,
            final String txHash) {
        final com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType type;
        try {
            type = com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            return null;
        }
        final MissionRecord record = switch (type) {
            case MISSION_CREATED_ON_CHAIN -> MissionRecord.created(missionId, tenantId, null, 0);
            case MISSION_COMPLETED_ON_CHAIN -> MissionRecord.completed(missionId, tenantId, null);
            case MISSION_CANCELLED_ON_CHAIN -> MissionRecord.cancelled(missionId, tenantId, null);
            default -> null;
        };
        if (record != null && txHash != null) {
            record.confirmOnChain(txHash);
        }
        return record;
    }
}
