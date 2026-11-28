package com.yowyob.tiibntick.core.delivery.application.port.in;

import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DisputeParcelOutcome;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponsibleParty;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Parcel side of a dispute (spec §1.2 rows P12–P17, §1.1 row D21; decisions [B.7], QO-7, QO-16). Called by
 * dispute-core in the transaction of the dispute change (wiring: lot L4).
 */
public interface ParcelDisputeUseCase {

    /**
     * Who takes part in a parcel (spec §1.4 [E]): the people allowed to open a dispute on it.
     *
     * @param parcelId           the parcel
     * @param senderIds          sender actor ids
     * @param recipientActorIds  recipient actor ids (empty while deliveries do not carry it)
     * @param courierActorIds    actor ids of the couriers assigned to its deliveries
     * @param courierOrgIds      organisations the deliveries were assigned to (freelancer org)
     * @param relayOrgIds        Kernel organisations owning the relay that holds the parcel or is its destination
     */
    record DisputeParties(UUID parcelId, java.util.Set<UUID> senderIds, java.util.Set<UUID> recipientActorIds,
                          java.util.Set<UUID> courierActorIds, java.util.Set<String> courierOrgIds,
                          java.util.Set<UUID> relayOrgIds) {
    }

    /**
     * Resolves the core parcel of a dispute: {@code parcelId} when it is a parcel, else the parcel of delivery
     * {@code deliveryId}; empty when neither is a core parcel (e.g. a market order).
     */
    Mono<UUID> resolveParcelId(UUID tenantId, UUID parcelId, UUID deliveryId);

    /** @return the parties of the parcel, empty when it is not a core parcel */
    Mono<DisputeParties> disputeParties(UUID tenantId, UUID parcelId);

    /**
     * Row P12: the parcel is under dispute; with {@code freezesDelivery} (any cause other than a delay, QO-7) its
     * delivery accepts only a final failure until the outcome.
     *
     * @param withinFilingWindow for a DELIVERED or RETURNED parcel: whether the filing window is still open
     */
    Mono<Parcel> openDispute(UUID tenantId, UUID parcelId, UUID disputeId, boolean freezesDelivery,
                             boolean withinFilingWindow, TntEventActor actor, UUID causationId);

    /**
     * Row P12 with the filing window computed here (lot L4): a DELIVERED or RETURNED parcel accepts a dispute until
     * {@code statusChangedAt} + the tenant's {@code disputeFilingWindowDays}.
     */
    Mono<Parcel> openDispute(UUID tenantId, UUID parcelId, UUID disputeId, boolean freezesDelivery,
                             TntEventActor actor, UUID causationId);

    /**
     * Rows P13–P17 and their effects: LOST/DAMAGED end the current delivery (D21); RETURN_TO_SENDER opens the return
     * draft (QO-4); REDELIVERY creates a new delivery of the same parcel (kind REDELIVERY, D1).
     *
     * @param trigger            {@code DISPUTE_<resolution type>}
     * @param compensationAmount amount compensated (LOST/DAMAGED details), may be {@code null}
     * @param responsibleParty   party at fault (return cost [B.6], D21 failure), may be {@code null}
     */
    Mono<Parcel> applyDisputeOutcome(UUID tenantId, UUID parcelId, DisputeParcelOutcome outcome, String trigger,
                                     String compensationAmount, ResponsibleParty responsibleParty,
                                     TntEventActor actor, UUID causationId);
}
