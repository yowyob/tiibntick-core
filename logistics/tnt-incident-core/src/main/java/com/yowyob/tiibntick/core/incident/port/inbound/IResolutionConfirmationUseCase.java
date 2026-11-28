package com.yowyob.tiibntick.core.incident.port.inbound;

import com.yowyob.tiibntick.core.incident.domain.enums.IncidentDeliveryOutcome;
import com.yowyob.tiibntick.core.incident.domain.enums.IncidentResponsibleParty;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Resolution proposed by the manager and confirmed or contested by the declaring courier (rows I23–I25, QO-14), and
 * the courier's own closing of a non-blocking incident (row I6).
 */
public interface IResolutionConfirmationUseCase {

    /** I23: the manager proposes (from AGENCY_HANDLING or ESCALATED). */
    Mono<Incident> propose(UUID incidentId, UUID managerActorId, IncidentDeliveryOutcome outcome,
                           IncidentResponsibleParty responsibleParty, String note);

    /** I24: the declaring courier confirms. */
    Mono<Incident> confirm(UUID incidentId, UUID courierActorId);

    /** I25: the declaring courier contests; the manager takes it back. */
    Mono<Incident> contest(UUID incidentId, UUID courierActorId, String reason);

    /** I6: the declaring courier closes his own non-blocking incident (the delivery went on at triage). */
    Mono<Incident> resolveOwnNonBlocking(UUID incidentId, UUID courierActorId, String note);

    /** I24 by the system: confirms the proposals older than the configured delay; returns how many. */
    Mono<Long> confirmExpired(Instant now);
}
