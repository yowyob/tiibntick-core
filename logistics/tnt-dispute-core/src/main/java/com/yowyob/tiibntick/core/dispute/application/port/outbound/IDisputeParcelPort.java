package com.yowyob.tiibntick.core.dispute.application.port.outbound;

import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeResponsibleParty;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Parcel side of a dispute (spec §1.4, lot L4): replaces {@code IDeliveryStatusPort} and its two topics. Called in
 * the transaction of the dispute change, so the parcel and the dispute move together or not at all.
 */
public interface IDisputeParcelPort {

    /** Parties of a parcel allowed to open a dispute on it ([E]). */
    record Parties(String parcelId, Set<String> senderIds, Set<String> recipientActorIds, Set<String> courierActorIds,
                   Set<String> courierOrgIds, Set<String> relayOrgIds) {
    }

    /**
     * @return the core parcel of {@code packageId}, else the parcel of delivery {@code deliveryId}; empty when neither
     *         is a core parcel (market order, legacy identifier)
     */
    Mono<String> resolveParcel(String tenantId, String packageId, String deliveryId);

    /** @return the parties of the parcel, empty when it is not a core parcel */
    Mono<Parties> parties(String tenantId, String parcelId);

    /**
     * Row P12: the parcel goes UNDER_DISPUTE.
     *
     * @param freezesDelivery whether the delivery accepts only a final failure until the outcome (QO-7)
     */
    Mono<Void> openDispute(String tenantId, String parcelId, String disputeId, boolean freezesDelivery,
                           TntEventActor actor);

    /**
     * Rows P13–P17 when the parcel is still under this dispute (else nothing: a dispute opened before lot L4).
     *
     * @param outcome {@code RESTORE_PREVIOUS}, {@code LOST}, {@code DAMAGED}, {@code RETURN_TO_SENDER},
     *                {@code REDELIVERY}
     */
    Mono<Void> applyOutcome(String tenantId, String parcelId, String disputeId, String outcome, String trigger,
                            BigDecimal compensationAmount, DisputeResponsibleParty responsibleParty,
                            TntEventActor actor);
}
