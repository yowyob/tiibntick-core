package com.yowyob.tiibntick.core.sync.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.sync.application.port.out.IEntityVersionRepository;
import com.yowyob.tiibntick.core.sync.application.port.out.IOfflineOperationApplier;
import com.yowyob.tiibntick.core.sync.application.port.out.IOfflineOperationRepository;
import com.yowyob.tiibntick.core.sync.application.port.out.ISyncEventPublisher;
import com.yowyob.tiibntick.core.sync.domain.event.SyncConflictDetectedEvent;
import com.yowyob.tiibntick.core.sync.domain.model.ConflictRecord;
import com.yowyob.tiibntick.core.sync.domain.model.EntityVersionRecord;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.ConflictResolution;
import com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OfflineQueueDomainService {

    private static final Logger log = LoggerFactory.getLogger(OfflineQueueDomainService.class);

    private final IOfflineOperationRepository operationRepository;
    private final IEntityVersionRepository entityVersionRepository;
    private final ConflictResolverService conflictResolver;
    private final ISyncEventPublisher eventPublisher;
    private final List<IOfflineOperationApplier> appliers;
    private final ObjectMapper objectMapper;

    public OfflineQueueDomainService(IOfflineOperationRepository operationRepository,
                                     IEntityVersionRepository entityVersionRepository,
                                     ConflictResolverService conflictResolver,
                                     ISyncEventPublisher eventPublisher,
                                     List<IOfflineOperationApplier> appliers,
                                     ObjectMapper objectMapper) {
        this.operationRepository = operationRepository;
        this.entityVersionRepository = entityVersionRepository;
        this.conflictResolver = conflictResolver;
        this.eventPublisher = eventPublisher;
        this.appliers = appliers != null ? appliers : List.of();
        this.objectMapper = objectMapper;
    }

    public record ProcessResult(
            int applied,
            int conflicts,
            int discarded,
            List<ConflictRecord> conflictRecords
    ) {
        public static ProcessResult empty() {
            return new ProcessResult(0, 0, 0, new ArrayList<>());
        }
    }

    /**
     * Processes a batch of offline operations submitted by a client in a single push-sync.
     * Operations are processed in sequence number order (FIFO within the device's queue).
     *
     * @param operations the list of offline operations to process
     * @param sessionId  the sync session identifier for audit
     * @return Mono with the processing summary
     */
    public Mono<ProcessResult> processOperations(List<OfflineOperation> operations, String sessionId) {
        if (operations.isEmpty()) {
            return Mono.just(ProcessResult.empty());
        }

        List<OfflineOperation> sorted = operations.stream()
                .sorted(java.util.Comparator.comparingLong(OfflineOperation::getSequenceNumber))
                .toList();

        log.debug("Processing {} offline operations for session {}", sorted.size(), sessionId);

        List<ConflictRecord> allConflicts = new ArrayList<>();
        int[] stats = {0, 0, 0}; // [applied, conflicts, discarded]

        return Flux.fromIterable(sorted)
                .concatMap(op -> processOneOperation(op, sessionId)
                        .flatMap(conflictOpt -> {
                            if (conflictOpt.isEmpty()) {
                                stats[0]++;
                            } else {
                                ConflictRecord conflict = conflictOpt.get();
                                allConflicts.add(conflict);
                                if (conflict.resolution() == ConflictResolution.DISCARDED) {
                                    stats[2]++;
                                } else {
                                    stats[1]++;
                                }
                            }
                            return operationRepository.save(op);
                        })
                        .onErrorResume(ex -> {
                            log.error("Failed to process operation {}: {}", op.getId(), ex.getMessage());
                            op.markFailed(ex.getMessage());
                            stats[2]++;
                            return operationRepository.save(op);
                        })
                )
                .then(Mono.fromCallable(() -> new ProcessResult(stats[0], stats[1], stats[2], allConflicts)));
    }

    private Mono<Optional<ConflictRecord>> processOneOperation(OfflineOperation op, String sessionId) {
        op.markApplying();

        return entityVersionRepository
                .findCurrent(op.getTenantId(), op.getAggregateType(), op.getAggregateId())
                .flatMap(serverVersion -> handleConflict(op, serverVersion, sessionId))
                .switchIfEmpty(applyOperation(op).thenReturn(Optional.<ConflictRecord>empty()));
    }

    private Mono<Optional<ConflictRecord>> handleConflict(OfflineOperation op,
                                                                      EntityVersionRecord serverVersion,
                                                                      String sessionId) {
        if (!isConflicting(op, serverVersion)) {
            return applyOperation(op).thenReturn(Optional.<ConflictRecord>empty());
        }

        ConflictRecord conflict = conflictResolver.resolve(op, serverVersion);
        log.info("Conflict on {}/{}: resolution={}", op.getAggregateType(), op.getAggregateId(), conflict.resolution());

        op.markConflict();

        SyncConflictDetectedEvent event = new SyncConflictDetectedEvent(
                op.getTenantId(), sessionId, op.getUserId(),
                op.getAggregateType(), op.getAggregateId(), conflict.resolution());

        Mono<Void> publishConflict = eventPublisher.publish(event);

        Mono<Void> applyResolved = switch (conflict.resolution()) {
            case CLIENT_WINS -> applyClientValue(op, conflict.resolvedValue());
            case SERVER_WINS -> Mono.empty();
            case MANUAL_MERGE -> Mono.empty();
            case DISCARDED -> {
                op.markDiscarded("Conflict discarded: " + conflict.resolution());
                yield Mono.empty();
            }
        };

        if (conflict.resolution() == ConflictResolution.CLIENT_WINS) {
            op.markApplied();
        }

        return Mono.when(publishConflict, applyResolved)
                .thenReturn(Optional.of(conflict));
    }

    /**
     * Applies an offline operation. If a registered {@link IOfflineOperationApplier} claims
     * this operation's aggregate type, the real business use-case is invoked first (with
     * idempotent replay protection) and its response becomes the {@code entity_version}
     * payload; otherwise this preserves the original journal-only behavior (upsert the
     * client's raw payload into {@code entity_version} with no other side-effect).
     */
    private Mono<Void> applyOperation(OfflineOperation op) {
        return resolveEntityVersionPayload(op, op)
                .flatMap(payloadJson -> upsertEntityVersion(op, payloadJson))
                .doOnSuccess(v -> op.markApplied());
    }

    private Mono<Void> applyClientValue(OfflineOperation op, String resolvedPayload) {
        return resolveEntityVersionPayload(op, op.withPayload(resolvedPayload))
                .flatMap(payloadJson -> upsertEntityVersion(op, payloadJson));
    }

    /**
     * Determines the payload that should be journaled into {@code entity_version}: the
     * applier's real use-case response if one is registered for this aggregate type (skipping
     * re-invocation when the operation id was already applied in a previous push), or the raw
     * client payload ({@code fallbackOp.getPayload()}) when no applier is registered.
     *
     * @param op         the operation being processed, used for idempotency lookup and as the
     *                   fallback source of the current aggregate identity
     * @param applierOp  the operation view (possibly with a conflict-resolved payload) to hand
     *                   to the applier if one is invoked
     */
    private Mono<String> resolveEntityVersionPayload(OfflineOperation op, OfflineOperation applierOp) {
        Optional<IOfflineOperationApplier> applier = findApplier(op.getAggregateType());
        if (applier.isEmpty()) {
            return Mono.just(applierOp.getPayload());
        }

        return operationRepository.isAlreadyApplied(op.getId().value())
                .flatMap(alreadyApplied -> {
                    if (alreadyApplied) {
                        log.info("Offline operation {} already applied previously — skipping re-invocation of applier for idempotent replay",
                                op.getId());
                        return entityVersionRepository.findCurrent(op.getTenantId(), op.getAggregateType(), op.getAggregateId())
                                .map(EntityVersionRecord::payloadJson)
                                .switchIfEmpty(Mono.just(applierOp.getPayload()));
                    }
                    return applier.get().apply(applierOp);
                });
    }

    private Mono<Void> upsertEntityVersion(OfflineOperation op, String payloadJson) {
        EntityVersionRecord newVersion = new EntityVersionRecord(
                op.getTenantId(),
                op.getAggregateType(),
                op.getAggregateId(),
                System.currentTimeMillis(),
                toEntityOperation(op),
                payloadJson,
                LocalDateTime.now(),
                op.getUserId()
        );
        return entityVersionRepository.upsert(newVersion);
    }

    private Optional<IOfflineOperationApplier> findApplier(String aggregateType) {
        return appliers.stream()
                .filter(a -> a.supports(aggregateType))
                .findFirst();
    }

    private boolean isConflicting(OfflineOperation op, EntityVersionRecord serverVersion) {
        return serverVersion.updatedAt().isAfter(op.getLocalTimestamp())
                && !serverVersion.updatedByUserId().equals(op.getUserId());
    }

    private DeltaOperation toEntityOperation(OfflineOperation op) {
        return switch (op.getType()) {
            case MISSION_STATUS_UPDATE -> DeltaOperation.STATUS_CHANGED;
            case PACKAGE_SCAN, HUB_DEPOSIT, DELIVERY_CONFIRMATION -> DeltaOperation.STATUS_CHANGED;
            case GPS_UPDATE -> DeltaOperation.UPDATED;
            case ANOMALY_REPORT, FORM_SUBMISSION -> DeltaOperation.CREATED;
            case MARKET_COMMAND -> deriveMarketCommandDeltaOperation(op.getPayload());
        };
    }

    /**
     * Derives the {@link DeltaOperation} for a {@code MARKET_COMMAND} offline op from its
     * envelope's {@code commandName} field:
     * {@code { "commandName": "PLACE_ORDER", "aggregateType": "MARKET_ORDER", "command": {...} } }.
     * {@code commandName} prefix mapping: {@code CREATE_}/{@code PLACE_} → CREATED;
     * {@code CANCEL_}/{@code REJECT_}/{@code SUSPEND_}/{@code APPROVE_}/{@code CONFIRM_}/
     * {@code DISPATCH_}/{@code COMPLETE_}/{@code DELIVER_}, or a name containing
     * {@code STATUS} → STATUS_CHANGED; everything else → UPDATED.
     */
    private DeltaOperation deriveMarketCommandDeltaOperation(String payload) {
        String commandName = "";
        try {
            JsonNode node = objectMapper.readTree(payload);
            commandName = node.path("commandName").asText("");
        } catch (Exception e) {
            log.warn("Failed to parse MARKET_COMMAND payload envelope for commandName, defaulting to UPDATED: {}", e.getMessage());
        }

        String upper = commandName.toUpperCase();
        if (upper.startsWith("CREATE_") || upper.startsWith("PLACE_")) {
            return DeltaOperation.CREATED;
        }
        if (upper.startsWith("CANCEL_") || upper.startsWith("REJECT_") || upper.startsWith("SUSPEND_")
                || upper.startsWith("APPROVE_") || upper.startsWith("CONFIRM_") || upper.startsWith("DISPATCH_")
                || upper.startsWith("COMPLETE_") || upper.startsWith("DELIVER_") || upper.contains("STATUS")) {
            return DeltaOperation.STATUS_CHANGED;
        }
        return DeltaOperation.UPDATED;
    }
}
