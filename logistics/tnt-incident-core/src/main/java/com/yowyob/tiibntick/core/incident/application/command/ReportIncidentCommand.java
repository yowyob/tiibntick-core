package com.yowyob.tiibntick.core.incident.application.command;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import java.util.List;
import java.util.UUID;
/**
 * Command to report a new delivery incident.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Value @Builder
public class ReportIncidentCommand {
    @NotNull UUID tenantId;
    @NotNull UUID agencyId;
    @NotNull UUID missionId;
    @NotNull PlatformType platform;
    @NotNull IncidentType type;
    @NotNull String description;
    @NotNull UUID reportedByActorId;
    @NotNull ActorRole reportedByRole;
    List<UUID> affectedParcelIds;
    Double currentLat;
    Double currentLng;

    // : FreelancerOrg responsibility context
    /**
     * UUID of the FreelancerOrg responsible for this mission.
     * Null for Agency-dispatched missions.
     * Referenced from tnt-delivery-core's MissionStatusChangedEvent.
     */
    String responsibleOrgId;

    /**
     * Type of responsible org: "FREELANCER_ORG" | "AGENCY".
     * Null when responsibleOrgId is null.
     */
    String responsibleOrgType;
}
