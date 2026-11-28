package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryFailureReason;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Outbound port to the delivery lifecycle owned by {@code tnt-delivery-core}.
 *
 * <p>The delivery aggregate (status, transitions, tracking code, pickup/delivery instants and the
 * domain events other modules react to) lives in tnt-delivery-core, which is the single source of
 * truth: status, assigned delivery person, instants, agreed price and pickup / hand-over windows. gofp
 * keeps only what is specific to the Go-Freelancer product on its own row (OTP hashes, handover codes,
 * receive code, notes) and drives the lifecycle exclusively through this port, so an illegal transition
 * is refused by the core's state machine and every accepted one is published. Everything gofp shows about
 * a delivery's state is read back from the core through this port.
 *
 * @author MANFOUO Braun
 */
public interface IDeliveryLifecyclePort {

    /** What the core currently says about a delivery. */
    record CoreDeliveryState(
            UUID deliveryId,
            String status,
            String trackingCode,
            UUID deliveryPersonId,
            Instant actualPickupTime,
            Instant actualDeliveryTime,
            BigDecimal agreedAmount,
            String agreedCurrency,
            Instant pickupWindowStart,
            Instant pickupWindowEnd,
            Instant deliveryWindowStart,
            Instant deliveryWindowEnd,
            UUID announcementId,
            UUID parcelId,
            Double parcelVolumeM3,
            Integer relayStorageDays,
            Instant relayStorageExpiresAt,
            boolean atDestinationRelay) {

        /** State without the destination flag (the delivery is not at its destination relay). */
        public CoreDeliveryState(UUID deliveryId, String status, String trackingCode, UUID deliveryPersonId,
                                 Instant actualPickupTime, Instant actualDeliveryTime, BigDecimal agreedAmount,
                                 String agreedCurrency, Instant pickupWindowStart, Instant pickupWindowEnd,
                                 Instant deliveryWindowStart, Instant deliveryWindowEnd, UUID announcementId,
                                 UUID parcelId, Double parcelVolumeM3, Integer relayStorageDays,
                                 Instant relayStorageExpiresAt) {
            this(deliveryId, status, trackingCode, deliveryPersonId, actualPickupTime, actualDeliveryTime,
                    agreedAmount, agreedCurrency, pickupWindowStart, pickupWindowEnd, deliveryWindowStart,
                    deliveryWindowEnd, announcementId, parcelId, parcelVolumeM3, relayStorageDays,
                    relayStorageExpiresAt, false);
        }

        /** State without relay storage data. */
        public CoreDeliveryState(UUID deliveryId, String status, String trackingCode, UUID deliveryPersonId,
                                 Instant actualPickupTime, Instant actualDeliveryTime, BigDecimal agreedAmount,
                                 String agreedCurrency, Instant pickupWindowStart, Instant pickupWindowEnd,
                                 Instant deliveryWindowStart, Instant deliveryWindowEnd, UUID announcementId,
                                 UUID parcelId, Double parcelVolumeM3) {
            this(deliveryId, status, trackingCode, deliveryPersonId, actualPickupTime, actualDeliveryTime,
                    agreedAmount, agreedCurrency, pickupWindowStart, pickupWindowEnd, deliveryWindowStart,
                    deliveryWindowEnd, announcementId, parcelId, parcelVolumeM3, null, null);
        }

        /** State with no agreed terms, announcement nor parcel data. */
        public CoreDeliveryState(UUID deliveryId, String status, String trackingCode, UUID deliveryPersonId,
                                 Instant actualPickupTime, Instant actualDeliveryTime) {
            this(deliveryId, status, trackingCode, deliveryPersonId, actualPickupTime, actualDeliveryTime,
                    null, null, null, null, null, null, null, null, null, null, null);
        }

        /** The status as an enum, or {@code null} when the core sent a value this build does not know. */
        public DeliveryStatus statusValue() {
            try {
                return status == null ? null : DeliveryStatus.fromValue(status);
            } catch (IllegalArgumentException unknown) {
                return null;
            }
        }
    }

    Mono<CoreDeliveryState> findState(UUID tenantId, UUID deliveryId);

    /**
     * The core's state of this delivery whatever its tenant, or empty when the core does not know it.
     * For readers with no tenant context (background consumers, rows read by id).
     */
    Mono<CoreDeliveryState> findStateAnyTenant(UUID deliveryId);

    /**
     * The core deliveries that carry this parcel, whatever their tenant, most recent first. This is how a
     * relay deposit (keyed on the parcel id) finds its delivery, whatever parcel the core created.
     * <p><b>Reserved</b> for the retrieval of a parcel deposited at a relay point (a deposit is keyed on the
     * parcel id and has no tenant context). It crosses tenants: do not call it from anywhere else
     * ({@code ParcelLookupCallersArchitectureTest} fails the build otherwise).
     */
    Flux<CoreDeliveryState> findStatesByParcelAnyTenant(UUID parcelId);

    /** Deliveries assigned to this delivery person (any status), newest first. */
    Flux<CoreDeliveryState> findByDeliveryPerson(UUID tenantId, UUID deliveryPersonId);

    /** Deliveries of the tenant in this status, newest first. */
    Flux<CoreDeliveryState> findByStatus(UUID tenantId, DeliveryStatus status);

    /** Deliveries of these delivery persons that are neither delivered, failed nor cancelled. */
    Mono<Long> countUnfinished(UUID tenantId, Collection<UUID> deliveryPersonIds);

    /** Deliveries of these delivery persons that reached DELIVERED. */
    Mono<Long> countDelivered(UUID tenantId, Collection<UUID> deliveryPersonIds);

    /**
     * Records the terms agreed for the delivery (price, pickup and hand-over windows). A null amount
     * leaves the recorded price unchanged. No status change.
     */
    Mono<CoreDeliveryState> recordAgreedTerms(UUID tenantId, UUID deliveryId, BigDecimal amount, String currency,
                                              Instant pickupWindowStart, Instant pickupWindowEnd,
                                              Instant deliveryWindowStart, Instant deliveryWindowEnd);

    /** Lot C-56 — tenant the delivery lives in (the shipper's), or empty if unknown. */
    default Mono<UUID> tenantOf(UUID deliveryId) {
        return Mono.empty();
    }

    /** ASSIGNED -> PICKED_UP (row D4). */
    Mono<CoreDeliveryState> confirmPickup(UUID tenantId, UUID deliveryId, UUID deliveryPersonId);

    /** PICKED_UP -> IN_TRANSIT (D5), or AT_RELAY_HUB -> IN_TRANSIT (D6, resume after a relay stop). */
    Mono<CoreDeliveryState> startOrResumeTransit(UUID tenantId, UUID deliveryId, UUID deliveryPersonId);

    /**
     * IN_TRANSIT -> AT_RELAY_HUB (row D7). At the destination relay the core starts the storage:
     * min(requested, storage of the relay) days (QO-30); the state returned carries them.
     *
     * @param requestedStorageDays storage asked by the sender, {@code null} = the relay's storage
     */
    Mono<CoreDeliveryState> depositAtRelayHub(UUID tenantId, UUID deliveryId, UUID deliveryPersonId,
                                              UUID relayHubId, Integer requestedStorageDays);

    /** AT_RELAY_HUB or DELIVERY_ATTEMPT_FAILED -> OUT_FOR_DELIVERY (row D8). */
    Mono<CoreDeliveryState> startFinalLeg(UUID tenantId, UUID deliveryId, UUID deliveryPersonId);

    /** IN_TRANSIT, OUT_FOR_DELIVERY or AT_RELAY_HUB -> DELIVERED (row D11). */
    Mono<CoreDeliveryState> complete(UUID tenantId, UUID deliveryId, UUID deliveryPersonId);

    /**
     * A failed hand-over attempt (rows D9/D10): DELIVERY_ATTEMPT_FAILED, or FAILED on a refusal by the recipient or
     * when the maximum of attempts is reached.
     */
    Mono<CoreDeliveryState> recordFailedAttempt(UUID tenantId, UUID deliveryId, UUID deliveryPersonId,
                                                DeliveryFailureReason reason);

    Mono<CoreDeliveryState> cancel(UUID tenantId, UUID deliveryId, UUID requesterId, String reason);
}
