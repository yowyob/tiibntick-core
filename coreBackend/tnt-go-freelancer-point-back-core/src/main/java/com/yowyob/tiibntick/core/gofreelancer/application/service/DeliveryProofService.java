package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.DeliveryProofRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryProof;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryProof.Kind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Photos and signatures a delivery person captures as proof of a hand-over, tied to the delivery.
 *
 * <ul>
 *   <li>Only the delivery person assigned to the delivery may add a proof; the author is the caller,
 *       never a value from the request.</li>
 *   <li>The assigned delivery person and the sender may read them (same parties as a cancellation).</li>
 *   <li>A file is attached to a delivery at most once, so an offline queue replayed twice, or an upload
 *       retried after a lost response, does not duplicate a proof.</li>
 * </ul>
 * The bytes live in the Kernel file storage ({@code POST /api/v1/media/evidence}); only the file id is kept here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryProofService {

    static final int MAX_NOTE_LENGTH = 500;
    /** Device clocks drift: a capture time further in the future than this is replaced by the server time. */
    static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private final DeliveryProofRepository repository;
    private final DeliveryStatusApplicationService deliveryStatus;

    public record NewProof(Kind kind, UUID fileId, String note, Instant capturedAt) {}

    public record ProofView(UUID id, UUID deliveryId, String kind, UUID fileId, String note,
                            Instant capturedAt, UUID uploadedBy) {
        static ProofView of(DeliveryProof p) {
            return new ProofView(p.getId(), p.getDeliveryId(), p.getKind(), p.getFileId(), p.getNote(),
                    p.getCapturedAt(), p.getUploadedBy());
        }
    }

    public Mono<ProofView> add(UUID deliveryId, UUID callerActorId, NewProof proof) {
        return addAll(deliveryId, callerActorId, List.of(proof)).next();
    }

    /** Attaches several proofs after a single ownership check; keeps the input order. */
    public Flux<ProofView> addAll(UUID deliveryId, UUID callerActorId, List<NewProof> proofs) {
        return Flux.defer(() -> {
            proofs.forEach(DeliveryProofService::validate);
            return deliveryStatus.assertCallerIsAssignedDeliveryPerson(deliveryId, callerActorId)
                    .thenMany(Flux.fromIterable(proofs)
                            .concatMap(p -> store(deliveryId, callerActorId, p)));
        });
    }

    public Flux<ProofView> list(UUID deliveryId, UUID callerActorId) {
        return deliveryStatus.assertCallerCanCancelDelivery(deliveryId, callerActorId)
                .thenMany(Flux.defer(() -> repository.findByDeliveryIdOrderByCapturedAtAsc(deliveryId)))
                .map(ProofView::of);
    }

    private Mono<ProofView> store(UUID deliveryId, UUID caller, NewProof p) {
        return repository.findByDeliveryIdAndFileId(deliveryId, p.fileId())
                .switchIfEmpty(Mono.defer(() -> {
                    Instant now = Instant.now();
                    Instant captured = p.capturedAt() == null || p.capturedAt().isAfter(now.plus(CLOCK_SKEW))
                            ? now : p.capturedAt();
                    return repository.save(DeliveryProof.builder()
                            .id(UUID.randomUUID())
                            .deliveryId(deliveryId)
                            .kind(p.kind().name())
                            .fileId(p.fileId())
                            .note(blankToNull(p.note()))
                            .capturedAt(captured)
                            .uploadedBy(caller)
                            .createdAt(now)
                            .build());
                }))
                .map(ProofView::of);
    }

    private static void validate(NewProof p) {
        if (p == null || p.kind() == null) {
            throw new TntValidationException("Proof kind is required");
        }
        if (p.fileId() == null) {
            throw new TntValidationException("Proof fileId is required");
        }
        if (p.note() != null && p.note().length() > MAX_NOTE_LENGTH) {
            throw new TntValidationException("Proof note is limited to " + MAX_NOTE_LENGTH + " characters");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
