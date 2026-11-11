package com.yowyob.tiibntick.core.gofreelancer.adapter.in.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryProofService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryProofService.NewProof;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryStatusApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import com.yowyob.tiibntick.core.sync.application.port.out.IOfflineOperationApplier;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.enums.OfflineOpType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Replays a delivery person's offline queue against the real delivery use-cases (aggregate
 * {@value GofpOfflineOpContracts#AGGREGATE_DELIVERY}).
 *
 * <ul>
 *   <li><b>Who</b> — the operation's {@code userId} (already bound to the JWT by the sync controller) must be the
 *       actor behind the assigned delivery person, exactly like the online endpoint; a queue can never move a
 *       delivery its owner does not carry.</li>
 *   <li><b>What</b> — status operations go through {@link DeliveryStatusApplicationService#updateStatus}, so the
 *       OTP, the delivery-core state machine, settlement and notifications behave as online.</li>
 *   <li><b>Replay safety</b> — the sync layer skips an operation id it already applied; this applier also treats a
 *       status the delivery already reached (or passed) as a no-op returning the current state, so a lost
 *       response, a duplicated queue or out-of-order retry never re-runs side effects. A genuine contradiction
 *       (e.g. FAILED after DELIVERED) is not hidden: the core refuses it and the operation is marked failed.</li>
 * </ul>
 */
@Component
public class GofpDeliveryOfflineOperationApplier implements IOfflineOperationApplier {

    private static final Logger log = LoggerFactory.getLogger(GofpDeliveryOfflineOperationApplier.class);

    private final DeliveryStatusApplicationService statusService;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryProofService proofService;
    private final ObjectMapper objectMapper;

    public GofpDeliveryOfflineOperationApplier(DeliveryStatusApplicationService statusService,
                                               DeliveryRepository deliveryRepository,
                                               DeliveryProofService proofService,
                                               ObjectMapper objectMapper) {
        this.statusService = statusService;
        this.deliveryRepository = deliveryRepository;
        this.proofService = proofService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String aggregateType) {
        return GofpOfflineOpContracts.AGGREGATE_DELIVERY.equals(aggregateType);
    }

    @Override
    public Mono<String> apply(OfflineOperation op) {
        return Mono.defer(() -> {
            OfflineOpType type = op.getType();
            boolean status = GofpOfflineOpContracts.STATUS_TYPES.contains(type);
            if (!status && !GofpOfflineOpContracts.JOURNAL_TYPES.contains(type)) {
                return Mono.error(new TntValidationException("Delivery applier does not support type: " + type));
            }
            UUID deliveryId = GofpOfflineOpContracts.parseUuid(op.getAggregateId(), "aggregateId");
            UUID caller = parseCaller(op.getUserId());
            DeliveryStatusUpdateDTO dto = status
                    ? GofpOfflineOpContracts.parseStatusUpdate(type, op.getPayload(), objectMapper)
                    : null;
            List<NewProof> proofs = status
                    ? GofpOfflineOpContracts.parseProofs(op.getPayload(), objectMapper)
                    : List.of();

            log.debug("Applying offline {} on delivery {} (opId={})", type, deliveryId, op.getId());
            // Deferred: nothing about the delivery is read or changed before ownership is proven.
            Mono<String> work = Mono.defer(() -> status ? applyStatus(deliveryId, caller, dto, proofs) : Mono.just(op.getPayload()));
            return statusService.assertCallerIsAssignedDeliveryPerson(deliveryId, caller).then(work);
        });
    }

    /**
     * Moves the delivery, then attaches the proofs captured with it. Proofs are recorded even when the step
     * was already reached (a photo that reached the server after its status), and only once the status was
     * accepted: a refused transition leaves no orphan evidence.
     */
    private Mono<String> applyStatus(UUID deliveryId, UUID caller, DeliveryStatusUpdateDTO dto, List<NewProof> proofs) {
        return deliveryRepository.findById(deliveryId)
                .switchIfEmpty(Mono.error(new TntValidationException("Delivery not found: " + deliveryId)))
                .flatMap(current -> alreadyReached(current.getStatus(), dto.getStatus())
                        ? Mono.just(current)
                        : statusService.updateStatus(deliveryId, dto))
                .flatMap(updated -> proofs.isEmpty()
                        ? Mono.just(updated)
                        : proofService.addAll(deliveryId, caller, proofs).then(Mono.just(updated)))
                .map(this::toJson);
    }

    /** True when the delivery already is at, or beyond, the requested forward step. */
    static boolean alreadyReached(DeliveryStatus current, DeliveryStatus target) {
        if (current == target) {
            return true;
        }
        int c = GofpOfflineOpContracts.forwardRank(current);
        int t = GofpOfflineOpContracts.forwardRank(target);
        return c >= 0 && t >= 0 && c >= t;
    }

    private UUID parseCaller(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (RuntimeException e) {
            throw new AccessDeniedException("Offline operation is not bound to an authenticated actor");
        }
    }

    private String toJson(Delivery delivery) {
        try {
            return objectMapper.writeValueAsString(delivery);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize delivery for offline apply", e);
        }
    }
}
