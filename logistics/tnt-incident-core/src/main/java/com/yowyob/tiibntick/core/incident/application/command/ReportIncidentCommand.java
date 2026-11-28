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
    /** Null for GO/FREELANCER-platform missions, which have no owning agency. */
    UUID agencyId;
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
     * Read from the delivery of tnt-delivery-core.
     */
    String responsibleOrgId;

    /**
     * Type of responsible org: "FREELANCER_ORG" | "AGENCY".
     * Null when responsibleOrgId is null.
     */
    String responsibleOrgType;

    /**
     * Tracking code of the delivery this incident blocks (format TNT-YYYYMMDD-XXXXXXXX).
     * Read from the delivery's parcel (tnt-delivery-core). Null if unavailable.
     */
    String trackingCode;

    /** "I cannot go on": the courier forces the incident as blocking (QO-2). */
    boolean blockingForced;
}
