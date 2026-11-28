package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.HandoverCodesDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.CodedStatusException;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Lot C-53 — the owner of a delivery need reads the two handover codes of its delivery: the pickup
 * code (given to the courier when he takes the parcel) and the delivery code (passed on to the
 * recipient). Nobody else may read them, the elected courier included.
 *
 * <p>Order of checks: unknown need → 404; caller is not the owner → 403; no courier elected yet
 * (no delivery for the need) → 409 {@value #NO_COURIER_YET}. Strict ownership: unlike the other
 * need routes, this one does not follow {@code tnt.gofp.ownership-guard.enabled}.
 */
@Service
@RequiredArgsConstructor
public class HandoverCodeService {

    public static final String NO_COURIER_YET = "NO_COURIER_YET";

    /** Statuses in which the parcel has not been picked up yet: the pickup code is still needed. */
    private static final Set<DeliveryStatus> BEFORE_PICKUP = EnumSet.of(
            DeliveryStatus.CREATED, DeliveryStatus.ASSIGNED, DeliveryStatus.PAUSED_BY_INCIDENT,
            DeliveryStatus.INCIDENT_REPORTED);

    /** Statuses after which no code is needed any more. */
    private static final Set<DeliveryStatus> FINISHED = EnumSet.of(
            DeliveryStatus.DELIVERED, DeliveryStatus.CANCELLED, DeliveryStatus.FAILED);

    private final IDeliveryNeedRepository needs;
    private final DeliveryRepository deliveries;
    private final DeliveryOtpService otp;

    public Mono<HandoverCodesDTO> forOwner(UUID needId, UUID callerId) {
        return needs.findById(needId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("DeliveryNeed not found: " + needId)))
                .flatMap(need -> callerId != null && callerId.equals(need.getUserId())
                        ? Mono.just(need)
                        : Mono.error(new AccessDeniedException("This delivery need belongs to another user")))
                .flatMap(need -> deliveries.findByDeliveryNeedId(need.getId())
                        .filter(d -> d.getFreelancerId() != null)
                        .switchIfEmpty(Mono.error(new CodedStatusException(HttpStatus.CONFLICT, NO_COURIER_YET,
                                "No courier has been elected for this delivery need yet"))))
                .flatMap(this::codesOf);
    }

    private Mono<HandoverCodesDTO> codesOf(Delivery delivery) {
        DeliveryStatus status = delivery.getStatus() != null ? delivery.getStatus() : DeliveryStatus.CREATED;
        boolean pickupUseful = BEFORE_PICKUP.contains(status) && delivery.getActualPickupTime() == null;
        boolean deliveryUseful = !FINISHED.contains(status);
        if (!pickupUseful && !deliveryUseful) {
            return Mono.just(new HandoverCodesDTO(null, null, status.name()));
        }
        return otp.readHandoverCodes(delivery, pickupUseful, deliveryUseful)
                .map(c -> new HandoverCodesDTO(c.pickupCode(), c.deliveryCode(), status.name()));
    }
}
