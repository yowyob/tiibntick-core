package com.yowyob.tiibntick.core.incident.domain.model;

import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Record of a single escalation step, capturing source actor, target actor and reason.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IncidentEscalation {

    private UUID id;
    private UUID incidentId;
    private int escalationLevel;
    private UUID escalatedFromActorId;
    private ActorRole escalatedFromRole;
    private UUID escalatedToActorId;
    private ActorRole escalatedToRole;
    private Instant escalatedAt;
    private String reason;
    private boolean interAgency;
    private UUID targetAgencyId;

    /**
     * Creates a standard escalation record to a human actor.
     *
     * @param incidentId    the escalated incident
     * @param level         escalation level (1 = first, 2 = second, etc.)
     * @param fromActor     the actor initiating the escalation
     * @param fromRole      role of the initiating actor
     * @param toActor       the target actor (nullable if escalating to a role)
     * @param toRole        the target role
     * @param reason        escalation reason
     * @return the new escalation record
     */
    public static IncidentEscalation create(UUID incidentId, int level,
                                             UUID fromActor, ActorRole fromRole,
                                             UUID toActor, ActorRole toRole, String reason) {
        return IncidentEscalation.builder()
                .id(UUID.randomUUID())
                .incidentId(incidentId)
                .escalationLevel(level)
                .escalatedFromActorId(fromActor)
                .escalatedFromRole(fromRole)
                .escalatedToActorId(toActor)
                .escalatedToRole(toRole)
                .escalatedAt(Instant.now())
                .reason(reason)
                .interAgency(false)
                .build();
    }

    /**
     * Creates an inter-agency escalation record.
     *
     * @param incidentId      the escalated incident
     * @param level           escalation level
     * @param fromActor       the actor initiating the escalation
     * @param fromRole        role of the initiating actor
     * @param targetAgencyId  the target agency
     * @param reason          escalation reason
     * @return the new inter-agency escalation record
     */
    public static IncidentEscalation createInterAgency(UUID incidentId, int level,
                                                         UUID fromActor, ActorRole fromRole,
                                                         UUID targetAgencyId, String reason) {
        return IncidentEscalation.builder()
                .id(UUID.randomUUID())
                .incidentId(incidentId)
                .escalationLevel(level)
                .escalatedFromActorId(fromActor)
                .escalatedFromRole(fromRole)
                .escalatedAt(Instant.now())
                .reason(reason)
                .interAgency(true)
                .targetAgencyId(targetAgencyId)
                .build();
    }
}
