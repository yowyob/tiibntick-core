package com.yowyob.tiibntick.core.sync.domain.service;

import com.yowyob.tiibntick.core.sync.application.port.out.IEntityVersionRepository;
import com.yowyob.tiibntick.core.sync.application.port.out.IOfflineOperationRepository;
import com.yowyob.tiibntick.core.sync.application.port.out.ISyncEventPublisher;
import com.yowyob.tiibntick.core.sync.domain.event.SyncConflictDetectedEvent;
import com.yowyob.tiibntick.core.sync.domain.model.ConflictRecord;
import com.yowyob.tiibntick.core.sync.domain.model.EntityVersionRecord;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.ConflictResolution;
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

    public OfflineQueueDomainService(IOfflineOperationRepository operationRepository,
                                     IEntityVersionRepository entityVersionRepository,
                                     ConflictResolverService conflictResolver,
                                     ISyncEventPublisher eventPublisher) {
        this.operationRepository = operationRepository;
        this.entityVersionRepository = entityVersionRepository;
        this.conflictResolver = conflictResolver;
        this.eventPublisher = eventPublisher;
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

    private Mono<Void> applyOperation(OfflineOperation op) {
        EntityVersionRecord newVersion = new EntityVersionRecord(
                op.getTenantId(),
                op.getAggregateType(),
                op.getAggregateId(),
                System.currentTimeMillis(),
                toEntityOperation(op),
                op.getPayload(),
                LocalDateTime.now(),
                op.getUserId()
        );
        op.markApplied();
        return entityVersionRepository.upsert(newVersion);
    }

    private Mono<Void> applyClientValue(OfflineOperation op, String resolvedPayload) {
        EntityVersionRecord resolved = new EntityVersionRecord(
                op.getTenantId(),
                op.getAggregateType(),
                op.getAggregateId(),
                System.currentTimeMillis(),
                toEntityOperation(op),
                resolvedPayload,
                LocalDateTime.now(),
                op.getUserId()
        );
        return entityVersionRepository.upsert(resolved);
    }

    private boolean isConflicting(OfflineOperation op, EntityVersionRecord serverVersion) {
        return serverVersion.updatedAt().isAfter(op.getLocalTimestamp())
                && !serverVersion.updatedByUserId().equals(op.getUserId());
    }

    private com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation toEntityOperation(OfflineOperation op) {
        return switch (op.getType()) {
            case MISSION_STATUS_UPDATE -> com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation.STATUS_CHANGED;
            case PACKAGE_SCAN, HUB_DEPOSIT, DELIVERY_CONFIRMATION -> com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation.STATUS_CHANGED;
            case GPS_UPDATE -> com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation.UPDATED;
            case ANOMALY_REPORT, FORM_SUBMISSION -> com.yowyob.tiibntick.core.sync.domain.model.enums.DeltaOperation.CREATED;
        };
    }
}
