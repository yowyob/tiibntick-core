package com.yowyob.tiibntick.core.incident.domain.model;

import com.yowyob.tiibntick.core.incident.domain.enums.ActorRole;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Actor added to an incident who must be notified of relevant state changes.
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
public class IncidentParticipant {

    private UUID id;
    private UUID incidentId;
    private UUID actorId;
    private ActorRole actorRole;
    private Instant addedAt;
    private Instant notifiedAt;
    private boolean hasResponded;
    private Instant responseAt;

    // ── : Organization context ───────────────────────────────────────────
    /** UUID of the organization this participant belongs to (Agency or FreelancerOrg). */
    private String orgId;
    /** Type: "AGENCY" | "FREELANCER_ORG". Null for individual participants. */
    private String orgType;

    public static IncidentParticipant of(UUID incidentId, UUID actorId, ActorRole role) {
        return of(incidentId, actorId, role, null, null);
    }

    /**
     * Creates a participant with organization context ().
     *
     * @param orgId   UUID of the org this participant belongs to (null if individual)
     * @param orgType "AGENCY" | "FREELANCER_ORG"
     */
    public static IncidentParticipant of(UUID incidentId, UUID actorId, ActorRole role,
                                          String orgId, String orgType) {
        return IncidentParticipant.builder()
                .id(UUID.randomUUID())
                .incidentId(incidentId)
                .actorId(actorId)
                .actorRole(role)
                .addedAt(Instant.now())
                .hasResponded(false)
                .orgId(orgId)
                .orgType(orgType)
                .build();
    }

    public IncidentParticipant markNotified() {
        return toBuilder().notifiedAt(Instant.now()).build();
    }

    public IncidentParticipant markResponded() {
        return toBuilder().hasResponded(true).responseAt(Instant.now()).build();
    }

    public IncidentParticipantBuilder toBuilder() {
        return IncidentParticipant.builder().id(id).incidentId(incidentId).actorId(actorId)
                .actorRole(actorRole).addedAt(addedAt).notifiedAt(notifiedAt)
                .hasResponded(hasResponded).responseAt(responseAt)
                .orgId(orgId).orgType(orgType);
    }
}
