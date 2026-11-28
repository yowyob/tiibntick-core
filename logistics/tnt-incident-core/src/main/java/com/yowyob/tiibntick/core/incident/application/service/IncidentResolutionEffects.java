package com.yowyob.tiibntick.core.incident.application.service;

import com.yowyob.tiibntick.core.incident.domain.enums.IncidentDeliveryOutcome;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import com.yowyob.tiibntick.core.incident.port.outbound.IDeliveryStatusPort;
import com.yowyob.tiibntick.core.incident.port.outbound.IPaymentFreezePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * What a resolved or cancelled incident does to its delivery and payment (spec §1.3, rows D14/D16/D18/D20), shared by
 * every resolution path so that they all behave the same.
 */
@Component
@RequiredArgsConstructor
public class IncidentResolutionEffects {

    private final IDeliveryStatusPort deliveryStatusPort;
    private final IPaymentFreezePort paymentFreezePort;

    /**
     * After a move to RESOLVED: {@code RESUMED} resumes a blocked delivery (D16, or D18 after a hand-over, with the
     * new courier if any); {@code FAILED} fails it (D20, return payer from the responsible party); {@code DISPUTE}
     * leaves delivery and money to the dispute (QO-15). The payment frozen by a blocking incident is unfrozen except
     * for a dispute.
     */
    public Mono<Incident> afterResolved(Incident resolved, UUID newDriverId, UUID newVehicleId) {
        return afterResolved(resolved, newDriverId, newVehicleId, null, null);
    }

    /**
     * Same, after a completed hand-over: the point where the parcel changed hands lets the delivery measure the legs
     * of the couriers (QO-11 / D-153).
     */
    public Mono<Incident> afterResolved(Incident resolved, UUID newDriverId, UUID newVehicleId,
                                        Double handoverLatitude, Double handoverLongitude) {
        IncidentDeliveryOutcome outcome = resolved.getDeliveryOutcome();
        if (outcome == null || outcome == IncidentDeliveryOutcome.DISPUTE) {
            return Mono.just(resolved);
        }
        Mono<Void> delivery = outcome == IncidentDeliveryOutcome.FAILED
                ? deliveryStatusPort.failDelivery(resolved.getMissionId(), resolved.getId(),
                resolved.getResponsibleParty() == null ? null : resolved.getResponsibleParty().name())
                : resolved.isBlocking() && handoverLatitude != null && handoverLongitude != null
                ? deliveryStatusPort.resumeAfterHandover(resolved.getMissionId(), newDriverId, newVehicleId,
                handoverLatitude, handoverLongitude)
                : resolved.isBlocking()
                ? deliveryStatusPort.resumeDelivery(resolved.getMissionId(),
                IDeliveryStatusPort.TRIGGER_INCIDENT_RESOLVED, newDriverId, newVehicleId)
                : Mono.empty(); // not blocking: the delivery already went on at triage (D14)
        return delivery
                .then(unfreezeIfBlocking(resolved, "Incident " + resolved.getReferenceCode() + " resolved"))
                .thenReturn(resolved);
    }

    /** After I31: the delivery goes back to its memorised state (D14/D16), the payment is unfrozen. */
    public Mono<Incident> afterCancelled(Incident cancelled, String reason) {
        return deliveryStatusPort.resumeDelivery(cancelled.getMissionId(),
                        IDeliveryStatusPort.TRIGGER_INCIDENT_CANCELLED, null, null)
                .then(unfreezeIfBlocking(cancelled, "Incident cancelled: " + reason))
                .thenReturn(cancelled);
    }

    private Mono<Void> unfreezeIfBlocking(Incident incident, String reason) {
        return incident.isBlocking() ? paymentFreezePort.unfreezePayment(incident.getMissionId(), reason) : Mono.empty();
    }
}
