package com.yowyob.tiibntick.core.incident.domain.model;

import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.domain.valueobject.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for the incident bounded context. Encapsulates the full lifecycle of a delivery incident.
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
public class Incident {

    private UUID id;
    /** Human-readable unique reference (e.g. TNT-INC-042381) displayed in all notifications. */
    private String referenceCode;
    /** Identifier of the owning tenant (multi-tenancy support). */
    private UUID tenantId;
    /** Identifier of the agency responsible for resolving this incident. */
    private UUID agencyId;
    /** The TiiBnTick platform that originated the incident (GO, FREELANCER, POINT, AGENCY). */
    private PlatformType sourcePlatform;
    /** Identifier of the delivery mission that is blocked by this incident. */
    private UUID missionId;
    private IncidentCategory category;
    private IncidentType type;
    /** Computed severity level, updated during triage. */
    private IncidentSeverity severity;
    /** Current FSM state of the incident lifecycle. */
    private IncidentStatus status;
    private ResolutionMode resolutionMode;
    private UUID reportedByActorId;
    private ActorRole reportedByRole;
    private String description;
    @Builder.Default
    /** List of parcel identifiers whose delivery chain is interrupted by this incident. */
    private List<UUID> affectedParcelIds = new ArrayList<>();
    /** True when more than one parcel is affected, triggering a dedicated blockchain chain. */
    private boolean multiParcelIncident;
    /** Identifier of the incident-dedicated blockchain chain (null for single-parcel incidents). */
    private String ownBlockchainChainId;
    private Instant reportedAt;
    private Instant detectedAt;
    private Instant acknowledgedAt;
    private Instant triagedAt;
    private Instant resolvedAt;
    private Instant closedAt;
    /** Number of escalation steps performed (0 = not yet escalated). */
    private int lastEscalationLevel;
    /** Number of times the automated engine attempted to resolve this incident. */
    private int autoResolutionAttempts;
    /** True when at least one inter-agency cooperation has been initiated. */
    private boolean interAgencyInvolved;
    /** Geographic context captured at incident detection time. */
    private IncidentGeoSnapshot geoSnapshot;
    /** SLA assessment: delay, revised deadline and breach indicators. */
    private IncidentSlaImpact slaImpact;
    /** Multi-factor risk score guiding the choice between automatic and manual resolution. */
    private IncidentRiskScore riskScore;
    /** Estimated financial liability and coverage for this incident. */
    private IncidentCompensationImpact compensationImpact;
    /** Optimistic locking version for concurrent updates. */
    private long version;

    // ── : FreelancerOrg responsibility context ────────────────────────────

    /**
     * UUID of the organization responsible for this incident.
     * Null when the incident is on an Agency-dispatched mission.
     * FreelancerOrgId when the incident is on a FreelancerOrg mission.
     * References tnt-organization-core UUID — pure integration key (no join).
     */
    private String responsibleOrgId;

    /**
     * Type of the responsible organization: "AGENCY" | "FREELANCER_ORG".
     * Null when responsibleOrgId is null.
     */
    private String responsibleOrgType;

    public static Incident create(
            UUID tenantId, UUID agencyId, PlatformType platform,
            UUID missionId, IncidentCategory category, IncidentType type,
            String description, UUID reportedBy, ActorRole reportedByRole,
            List<UUID> affectedParcelIds) {
        return createWithFreelancerOrg(tenantId, agencyId, platform, missionId, category, type,
                description, reportedBy, reportedByRole, affectedParcelIds, null, null);
    }

    /**
     * Creates an incident with FreelancerOrg responsibility context ().
     *
     * @param responsibleOrgId   UUID of the FreelancerOrg responsible, or null for Agency
     * @param responsibleOrgType "FREELANCER_ORG" or "AGENCY", or null
     */
    public static Incident createWithFreelancerOrg(
            UUID tenantId, UUID agencyId, PlatformType platform,
            UUID missionId, IncidentCategory category, IncidentType type,
            String description, UUID reportedBy, ActorRole reportedByRole,
            List<UUID> affectedParcelIds, String responsibleOrgId, String responsibleOrgType) {

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        return Incident.builder()
                .id(id)
                .referenceCode(generateReferenceCode())
                .tenantId(tenantId)
                .agencyId(agencyId)
                .sourcePlatform(platform)
                .missionId(missionId)
                .category(category)
                .type(type)
                .severity(IncidentSeverity.MEDIUM)
                .status(IncidentStatus.DETECTED)
                .description(description)
                .reportedByActorId(reportedBy)
                .reportedByRole(reportedByRole)
                .affectedParcelIds(affectedParcelIds != null ? new ArrayList<>(affectedParcelIds) : new ArrayList<>())
                .multiParcelIncident(affectedParcelIds != null && affectedParcelIds.size() > 1)
                .reportedAt(now)
                .detectedAt(now)
                .lastEscalationLevel(0)
                .autoResolutionAttempts(0)
                .interAgencyInvolved(false)
                .version(0L)
                .responsibleOrgId(responsibleOrgId)
                .responsibleOrgType(responsibleOrgType)
                .build();
    }

    /**
     * Transitions the incident from any non-terminal state to {@code ACKNOWLEDGED}.
     *
     * @return updated incident with {@code ACKNOWLEDGED} status and acknowledgement timestamp
     * @throws IllegalStateException if the incident is already in a terminal state
     */
    public Incident acknowledge() {
        validateTransition(IncidentStatus.ACKNOWLEDGED);
        return toBuilder().status(IncidentStatus.ACKNOWLEDGED).acknowledgedAt(Instant.now()).build();
    }

    /**
     * Applies triage results: assigns severity, geo-snapshot and SLA impact.
     *
     * @param severity    computed severity level
     * @param geoSnapshot geographic coordinates at incident time
     * @param slaImpact   SLA delay assessment
     * @return updated incident in {@code TRIAGED} status
     */
    public Incident triage(IncidentSeverity severity, IncidentGeoSnapshot geoSnapshot, IncidentSlaImpact slaImpact) {
        validateTransition(IncidentStatus.TRIAGED);
        return toBuilder()
                .status(IncidentStatus.TRIAGED)
                .severity(severity)
                .geoSnapshot(geoSnapshot)
                .slaImpact(slaImpact)
                .triagedAt(Instant.now())
                .build();
    }

    /**
     * Attaches the computed risk score to the incident.
     *
     * @param riskScore the risk score computed by {@link com.yowyob.tiibntick.core.incident.domain.service.IncidentRiskScoringService}
     * @return updated incident carrying the risk score
     */
    public Incident withRiskScore(IncidentRiskScore riskScore) {
        return toBuilder().riskScore(riskScore).build();
    }

    /**
     * Transitions to {@code AUTO_RESOLVING} and increments the auto-resolution attempt counter.
     *
     * @return updated incident
     */
    public Incident startAutoResolution() {
        return toBuilder()
                .status(IncidentStatus.AUTO_RESOLVING)
                .autoResolutionAttempts(this.autoResolutionAttempts + 1)
                .build();
    }

    public Incident startReassigningDriver() {
        return toBuilder().status(IncidentStatus.REASSIGNING_DRIVER).build();
    }

    public Incident startAwaitingHandover() {
        return toBuilder().status(IncidentStatus.AWAITING_HANDOVER).build();
    }

    public Incident startRerouting() {
        return toBuilder().status(IncidentStatus.REROUTING).build();
    }

    public Incident startTransferringToHub() {
        return toBuilder().status(IncidentStatus.TRANSFERRING_TO_HUB).build();
    }

    public Incident markAutoResolutionFailed() {
        return toBuilder().status(IncidentStatus.AUTO_RESOLUTION_FAILED).build();
    }

    public Incident pendingAgencyAssignment() {
        return toBuilder().status(IncidentStatus.PENDING_AGENCY_ASSIGNMENT).build();
    }

    public Incident startAgencyHandling() {
        return toBuilder().status(IncidentStatus.AGENCY_HANDLING).build();
    }

    public Incident waitForInterAgency() {
        return toBuilder().status(IncidentStatus.WAITING_INTERAGENCY).interAgencyInvolved(true).build();
    }

    public Incident startInterAgencyCooperation() {
        return toBuilder().status(IncidentStatus.INTERAGENCY_IN_PROGRESS).build();
    }

    public Incident escalate(int level, ActorRole toRole) {
        return toBuilder()
                .status(IncidentStatus.ESCALATED)
                .lastEscalationLevel(level)
                .build();
    }

    /**
     * Marks the incident as {@code RESOLVED} with the chosen resolution mode.
     *
     * @param mode the resolution strategy used
     * @return updated incident with resolution timestamp
     * @throws IllegalStateException if the incident is already in a terminal state
     */
    public Incident resolve(ResolutionMode mode) {
        validateIsNotTerminal();
        return toBuilder()
                .status(IncidentStatus.RESOLVED)
                .resolutionMode(mode)
                .resolvedAt(Instant.now())
                .build();
    }

    /**
     * Transitions a {@code RESOLVED} incident to {@code CLOSED}, triggering evidence archival
     * and blockchain chain tail linkage for multi-parcel incidents.
     *
     * @return updated incident in {@code CLOSED} status
     * @throws IllegalStateException if the incident is not yet RESOLVED
     */
    public Incident close() {
        if (this.status != IncidentStatus.RESOLVED) {
            throw new IllegalStateException("Incident must be RESOLVED before CLOSED. Current: " + status);
        }
        return toBuilder().status(IncidentStatus.CLOSED).closedAt(Instant.now()).build();
    }

    /**
     * Cancels an incident that no longer requires resolution.
     *
     * @param reason human-readable cancellation reason
     * @return updated incident in {@code CANCELLED} status
     */
    public Incident cancel(String reason) {
        validateIsNotTerminal();
        return toBuilder().status(IncidentStatus.CANCELLED).closedAt(Instant.now()).build();
    }

    public Incident assignBlockchainChain(String chainId) {
        return toBuilder().ownBlockchainChainId(chainId).build();
    }

    public Incident withCompensationImpact(IncidentCompensationImpact impact) {
        return toBuilder().compensationImpact(impact).build();
    }

    /**
     * Returns {@code true} when this incident is currently blocking its associated delivery mission.
     *
     * @return {@code true} if the incident is active and beyond the initial DETECTED state
     */
    public boolean isBlockingMission() {
        return !status.isTerminal() && status != IncidentStatus.DETECTED;
    }

    /**
     * Returns {@code true} when the incident type mandates finding a replacement driver.
     *
     * @return {@code true} if driver replacement is required
     */
    public boolean requiresDriverReplacement() {
        return type == IncidentType.DRIVER_VOLUNTARY_WITHDRAWAL_BEFORE_PICKUP
                || type == IncidentType.DRIVER_VOLUNTARY_WITHDRAWAL_AFTER_PICKUP
                || type == IncidentType.DRIVER_SUDDEN_UNAVAILABILITY
                || type == IncidentType.DRIVER_MEDICAL_EMERGENCY
                || type == IncidentType.DRIVER_ACCIDENT_PHYSICAL
                || type == IncidentType.DRIVER_ARRESTED
                || type == IncidentType.DRIVER_DECEASED
                || type == IncidentType.DRIVER_PHONE_DEAD;
    }

    /**
     * Returns {@code true} if the incident affects multiple parcels and therefore
     * requires its own dedicated blockchain chain.
     *
     * @return {@code true} when {@code affectedParcelIds.size() > 1}
     */
    public boolean requiresOwnBlockchainChain() {
        return multiParcelIncident && affectedParcelIds.size() > 1;
    }

    public Incident addAffectedParcel(UUID parcelId) {
        List<UUID> updated = new ArrayList<>(this.affectedParcelIds);
        updated.add(parcelId);
        return toBuilder()
                .affectedParcelIds(updated)
                .multiParcelIncident(updated.size() > 1)
                .build();
    }

    private void validateTransition(IncidentStatus target) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Cannot transition terminal incident " + id + " to " + target);
        }
    }

    private void validateIsNotTerminal() {
        if (status.isTerminal()) {
            throw new IllegalStateException("Incident " + id + " is already terminal: " + status);
        }
    }

    private static String generateReferenceCode() {
        return "TNT-INC-" + String.format("%06d", (int)(Math.random() * 999999));
    }

    public IncidentBuilder toBuilder() {
        return Incident.builder()
                .id(id).referenceCode(referenceCode).tenantId(tenantId)
                .agencyId(agencyId).sourcePlatform(sourcePlatform).missionId(missionId)
                .category(category).type(type).severity(severity).status(status)
                .resolutionMode(resolutionMode).reportedByActorId(reportedByActorId)
                .reportedByRole(reportedByRole).description(description)
                .affectedParcelIds(new ArrayList<>(affectedParcelIds))
                .multiParcelIncident(multiParcelIncident).ownBlockchainChainId(ownBlockchainChainId)
                .reportedAt(reportedAt).detectedAt(detectedAt).acknowledgedAt(acknowledgedAt)
                .triagedAt(triagedAt).resolvedAt(resolvedAt).closedAt(closedAt)
                .lastEscalationLevel(lastEscalationLevel)
                .autoResolutionAttempts(autoResolutionAttempts)
                .interAgencyInvolved(interAgencyInvolved)
                .geoSnapshot(geoSnapshot).slaImpact(slaImpact).riskScore(riskScore)
                .compensationImpact(compensationImpact).version(version);
    }
}
