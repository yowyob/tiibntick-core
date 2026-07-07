package com.yowyob.tiibntick.core.incident.domain.model;

import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Assignment of an incident to a human or system handler within an agency.
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
public class IncidentAssignment {

    private UUID id;
    private UUID incidentId;
    private UUID assignedToActorId;
    private ActorRole assignedToRole;
    private UUID assignedByActorId;
    private Instant assignedAt;
    private Instant acceptedAt;
    private Instant completedAt;
    private boolean autoAssigned;
    private UUID agencyId;
    private boolean active;

    public static IncidentAssignment createAuto(UUID incidentId, UUID agencyId) {
        return IncidentAssignment.builder()
                .id(UUID.randomUUID())
                .incidentId(incidentId)
                .assignedToRole(ActorRole.SYSTEM)
                .assignedAt(Instant.now())
                .autoAssigned(true)
                .agencyId(agencyId)
                .active(true)
                .build();
    }

    public static IncidentAssignment createManual(UUID incidentId, UUID assignedTo,
                                                   ActorRole role, UUID assignedBy, UUID agencyId) {
        return IncidentAssignment.builder()
                .id(UUID.randomUUID())
                .incidentId(incidentId)
                .assignedToActorId(assignedTo)
                .assignedToRole(role)
                .assignedByActorId(assignedBy)
                .assignedAt(Instant.now())
                .autoAssigned(false)
                .agencyId(agencyId)
                .active(true)
                .build();
    }

    public IncidentAssignment accept() {
        return toBuilder().acceptedAt(Instant.now()).build();
    }

    public IncidentAssignment complete() {
        return toBuilder().completedAt(Instant.now()).active(false).build();
    }

    public IncidentAssignmentBuilder toBuilder() {
        return IncidentAssignment.builder().id(id).incidentId(incidentId)
                .assignedToActorId(assignedToActorId).assignedToRole(assignedToRole)
                .assignedByActorId(assignedByActorId).assignedAt(assignedAt)
                .acceptedAt(acceptedAt).completedAt(completedAt)
                .autoAssigned(autoAssigned).agencyId(agencyId).active(active);
    }
}
