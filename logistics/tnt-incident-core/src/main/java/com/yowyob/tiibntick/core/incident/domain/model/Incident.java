package com.yowyob.tiibntick.core.incident.domain.model;

import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.incident.domain.enums.*;
import com.yowyob.tiibntick.core.incident.domain.valueobject.*;
import lombok.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Public-facing tracking code of the delivery this incident is blocking (format
     * {@code TNT-YYYYMMDD-XXXXXXXX}). Threaded from the delivery of {@code tnt-delivery-core}
     * so it can be re-exposed to {@code tnt-dispute-core}
     * when an incident escalates into an automated dispute. Null for incidents reported
     * before this field existed or where the source mission has no tracking code.
     */
    private String trackingCode;

    // ── lot L3 (spec §1.3) ───────────────────────────────────────────────────

    /** Blocks the delivery (QO-2: type/severity rule, or forced by the courier "I cannot go on"). */
    private boolean blocking;
    /** Forced by the declaring courier at declaration ("I cannot go on"). */
    private boolean blockingForcedByCourier;
    /** What the resolution does to the delivery; set on every move to RESOLVED (or proposed in PENDING_CONFIRMATION). */
    private IncidentDeliveryOutcome deliveryOutcome;
    /** Party held responsible, sets the payer of a return [B.6]; may be {@code null}. */
    private IncidentResponsibleParty responsibleParty;
    /** Manager's note of the resolution (proposal, "transferred to dispute"…). */
    private String resolutionNote;
    /** When the resolution was proposed (QO-14: confirmed automatically after the configured delay). */
    private Instant resolutionProposedAt;

    /** Number of events published for this incident: the {@code aggregateVersion} of the last one (lot L3). */
    private long eventSequence;

    /** Changes waiting to be published on {@code tnt.incident.events} by the repository (not persisted). */
    @Builder.Default
    private List<IncidentTransition> pendingTransitions = new ArrayList<>();

    public static Incident create(
            UUID tenantId, UUID agencyId, PlatformType platform,
            UUID missionId, IncidentCategory category, IncidentType type,
            String description, UUID reportedBy, ActorRole reportedByRole,
            List<UUID> affectedParcelIds) {
        return createWithFreelancerOrg(tenantId, agencyId, platform, missionId, category, type,
                description, reportedBy, reportedByRole, affectedParcelIds, null, null, null);
    }

    /**
     * Row I1: the assigned courier declares an incident ({@code REPORTED}; lot L3, [D.1]).
     *
     * @param responsibleOrgId   UUID of the FreelancerOrg responsible, or null for Agency
     * @param responsibleOrgType "FREELANCER_ORG" or "AGENCY", or null
     * @param trackingCode       tracking code of the blocked delivery, or null if unavailable
     */
    public static Incident createWithFreelancerOrg(
            UUID tenantId, UUID agencyId, PlatformType platform,
            UUID missionId, IncidentCategory category, IncidentType type,
            String description, UUID reportedBy, ActorRole reportedByRole,
            List<UUID> affectedParcelIds, String responsibleOrgId, String responsibleOrgType,
            String trackingCode) {

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Incident incident = Incident.builder()
                .id(id)
                .referenceCode(generateReferenceCode(now))
                .tenantId(tenantId)
                .agencyId(agencyId)
                .sourcePlatform(platform)
                .missionId(missionId)
                .category(category)
                .type(type)
                .severity(IncidentSeverity.MEDIUM)
                .status(IncidentStatus.REPORTED)
                .description(description)
                .reportedByActorId(reportedBy)
                .reportedByRole(reportedByRole)
                .affectedParcelIds(affectedParcelIds != null ? new ArrayList<>(affectedParcelIds) : new ArrayList<>())
                .multiParcelIncident(affectedParcelIds != null && affectedParcelIds.size() > 1)
                .reportedAt(now)
                .lastEscalationLevel(0)
                .autoResolutionAttempts(0)
                .interAgencyInvolved(false)
                .version(0L)
                .eventSequence(1L)
                .responsibleOrgId(responsibleOrgId)
                .responsibleOrgType(responsibleOrgType)
                .trackingCode(trackingCode)
                .build();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", type == null ? null : type.name());
        incident.pendingTransitions.add(new IncidentTransition(null, IncidentStatus.REPORTED, null, "REPORT_INCIDENT",
                actorOf(reportedByRole, reportedBy), details, now));
        return incident;
    }

    /** The courier forces the incident as blocking at declaration ("I cannot go on", QO-2). */
    public Incident forceBlockingByCourier() {
        return toBuilder().blockingForcedByCourier(true).build();
    }

    /** Row I2: automatic acknowledgement. */
    public Incident acknowledge() {
        return moveTo(IncidentStatus.ACKNOWLEDGED, "AUTO_ACKNOWLEDGE", TntEventActor.system(), null,
                b -> b.acknowledgedAt(Instant.now()));
    }

    /**
     * Row I3: triage — severity, geo-snapshot, SLA impact and {@code blocking} (QO-2).
     *
     * @param blocking whether the incident blocks the delivery (see {@code IncidentBlockingPolicy})
     */
    public Incident triage(IncidentSeverity severity, IncidentGeoSnapshot geoSnapshot, IncidentSlaImpact slaImpact,
                           boolean blocking) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("severity", severity == null ? null : severity.name());
        details.put("blocking", blocking);
        return moveTo(IncidentStatus.TRIAGED, "TRIAGE", TntEventActor.system(), details,
                b -> b.severity(severity).geoSnapshot(geoSnapshot).slaImpact(slaImpact).blocking(blocking)
                        .triagedAt(Instant.now()));
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

    /** Rows I4: automatic resolution of a blocking incident without agency. */
    public Incident startAutoResolution() {
        return moveTo(IncidentStatus.AUTO_RESOLVING, "START_AUTO_RESOLUTION", TntEventActor.system(), null,
                b -> b.autoResolutionAttempts(this.autoResolutionAttempts + 1));
    }

    /** Rows I7 (from AUTO_RESOLVING) and I15 (hand-over aborted). */
    public Incident startReassigningDriver() {
        String trigger = status == IncidentStatus.AWAITING_HANDOVER ? "HANDOVER_ABORTED" : "REASSIGN_DRIVER";
        return moveTo(IncidentStatus.REASSIGNING_DRIVER, trigger, TntEventActor.system(), null, b -> { });
    }

    /** Rows I12 (a replacement accepts) and I13 (the agency designates one of its couriers). */
    public Incident startAwaitingHandover() {
        return moveTo(IncidentStatus.AWAITING_HANDOVER, "REPLACEMENT_ASSIGNED", TntEventActor.system(), null, b -> { });
    }

    /** Row I8. */
    public Incident startRerouting() {
        return moveTo(IncidentStatus.REROUTING, "REROUTE", TntEventActor.system(), null, b -> { });
    }

    /** Row I9 (QO-13). */
    public Incident startTransferringToHub() {
        return moveTo(IncidentStatus.TRANSFERRING_TO_HUB, "TRANSFER_TO_HUB", TntEventActor.system(), null, b -> { });
    }

    /** Row I10. */
    public Incident markAutoResolutionFailed() {
        return moveTo(IncidentStatus.AUTO_RESOLUTION_FAILED, "AUTO_RESOLUTION_FAILED", TntEventActor.system(), null,
                b -> { });
    }

    /** Rows I5 (blocking agency incident) and I11 (after a failed automatic resolution). */
    public Incident pendingAgencyAssignment() {
        return moveTo(IncidentStatus.PENDING_AGENCY_ASSIGNMENT, "AWAIT_MANAGER", TntEventActor.system(), null, b -> { });
    }

    /** Rows I18, I21 (cooperation refused or failed), I25 (resolution contested) and I28 (taken back after escalation). */
    public Incident startAgencyHandling() {
        return startAgencyHandling(managerActor(null), "START_HANDLING");
    }

    public Incident startAgencyHandling(TntEventActor actor, String trigger) {
        return moveTo(IncidentStatus.AGENCY_HANDLING, trigger, actor, null, b -> { });
    }

    /** Row I19. */
    public Incident waitForInterAgency() {
        return moveTo(IncidentStatus.WAITING_INTERAGENCY, "REQUEST_COOPERATION", managerActor(null), null,
                b -> b.interAgencyInvolved(true));
    }

    /** Row I20. */
    public Incident startInterAgencyCooperation() {
        return moveTo(IncidentStatus.INTERAGENCY_IN_PROGRESS, "COOPERATION_ACCEPTED", managerActor(null), null, b -> { });
    }

    /** Row I27: escalation, level + 1 (from ESCALATED too). */
    public Incident escalate(int level, ActorRole toRole) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("level", level);
        details.put("toRole", toRole == null ? null : toRole.name());
        return moveTo(IncidentStatus.ESCALATED, "ESCALATE", TntEventActor.system(), details,
                b -> b.lastEscalationLevel(level));
    }

    /** Legacy resolution (handover I14, rerouting I17): the delivery goes on. */
    public Incident resolve(ResolutionMode mode) {
        return resolve(mode, IncidentDeliveryOutcome.RESUMED, null, null, "RESOLVE", TntEventActor.system());
    }

    /**
     * Rows I6, I14, I16, I17, I22, I24, I26, I29: {@code RESOLVED}, with its delivery outcome (mandatory).
     *
     * @throws IllegalArgumentException when {@code outcome} is missing
     */
    public Incident resolve(ResolutionMode mode, IncidentDeliveryOutcome outcome, IncidentResponsibleParty responsible,
                            String note, String trigger, TntEventActor actor) {
        if (outcome == null) {
            throw new IllegalArgumentException("deliveryOutcome is mandatory to resolve incident " + id);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("deliveryOutcome", outcome.name());
        details.put("responsibleParty", responsible == null ? null : responsible.name());
        details.put("resolutionMode", mode == null ? null : mode.name());
        return moveTo(IncidentStatus.RESOLVED, trigger, actor, details,
                b -> b.resolutionMode(mode).deliveryOutcome(outcome).responsibleParty(responsible)
                        .resolutionNote(note != null ? note : resolutionNote).resolvedAt(Instant.now()));
    }

    /** Row I23 (QO-14): the manager proposes a resolution, the declaring courier confirms or contests it. */
    public Incident proposeResolution(IncidentDeliveryOutcome outcome, IncidentResponsibleParty responsible,
                                      String note, TntEventActor actor) {
        if (outcome == null) {
            throw new IllegalArgumentException("deliveryOutcome is mandatory to propose a resolution of " + id);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("deliveryOutcome", outcome.name());
        details.put("responsibleParty", responsible == null ? null : responsible.name());
        return moveTo(IncidentStatus.PENDING_CONFIRMATION, "PROPOSE_RESOLUTION", actor, details,
                b -> b.deliveryOutcome(outcome).responsibleParty(responsible).resolutionNote(note)
                        .resolutionProposedAt(Instant.now()));
    }

    /** Row I24: the declaring courier confirms (or the delay expired: {@code automatic}). */
    public Incident confirmResolution(TntEventActor actor, boolean automatic) {
        requireStatus(IncidentStatus.PENDING_CONFIRMATION);
        return resolve(ResolutionMode.MANUAL_AGENCY, deliveryOutcome, responsibleParty, resolutionNote,
                automatic ? "CONFIRMATION_TIMED_OUT" : "CONFIRM_RESOLUTION", automatic ? TntEventActor.system() : actor);
    }

    /** Row I25: the declaring courier contests; back to the manager. */
    public Incident contestResolution(String reason, TntEventActor actor) {
        requireStatus(IncidentStatus.PENDING_CONFIRMATION);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        return moveTo(IncidentStatus.AGENCY_HANDLING, "CONTEST_RESOLUTION", actor, details,
                b -> b.deliveryOutcome(null).responsibleParty(null).resolutionProposedAt(null));
    }

    /** Row I30. */
    public Incident close() {
        if (this.status != IncidentStatus.RESOLVED) {
            throw new IllegalStateException("Incident must be RESOLVED before CLOSED. Current: " + status);
        }
        return moveTo(IncidentStatus.CLOSED, "CLOSE", TntEventActor.system(), null, b -> b.closedAt(Instant.now()));
    }

    /** Row I31: erroneous declaration. */
    public Incident cancel(String reason) {
        return cancel(reason, TntEventActor.system());
    }

    public Incident cancel(String reason, TntEventActor actor) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        return moveTo(IncidentStatus.CANCELLED, "CANCEL_INCIDENT", actor, details, b -> b.closedAt(Instant.now()));
    }

    /** {@code IncidentEscalatedToDispute} (change without transition, spec §3.5). */
    public Incident recordEscalatedToDispute(String reason) {
        return recordEscalatedToDispute(reason, null);
    }

    /**
     * {@code IncidentEscalatedToDispute} with the evidence of the escalation ({@code details.fraudReason}, lot L8:
     * dispute-core opens the dispute from this event only).
     */
    public Incident recordEscalatedToDispute(String reason, String fraudReason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        details.put("fraudReason", fraudReason);
        return change("ESCALATED_TO_DISPUTE", "ESCALATE_TO_DISPUTE", TntEventActor.system(), details);
    }

    /** {@code IncidentHandoverConfirmed}: one side of the hand-over confirmed (spec §1.3). */
    public Incident recordHandoverConfirmed(String side, TntEventActor actor) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("side", side);
        return change("HANDOVER_CONFIRMED", "CONFIRM_HANDOVER", actor, details);
    }

    /** {@code IncidentHandlerAssigned}: the agency or a support agent takes the incident. */
    public Incident recordHandlerAssigned(UUID handlerId, TntEventActor actor) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("handlerId", handlerId == null ? null : handlerId.toString());
        return change("HANDLER_ASSIGNED", "ASSIGN_HANDLER", actor, details);
    }

    public Incident assignBlockchainChain(String chainId) {
        return toBuilder().ownBlockchainChainId(chainId).build();
    }

    public Incident withCompensationImpact(IncidentCompensationImpact impact) {
        return toBuilder().compensationImpact(impact).build();
    }

    /** @return the changes waiting to be published (read-only) */
    public List<IncidentTransition> getPendingTransitions() {
        return Collections.unmodifiableList(pendingTransitions);
    }

    /**
     * Returns {@code true} when this incident blocks its delivery (spec §1.3): {@code blocking} and still open
     * (neither RESOLVED nor terminal).
     */
    public boolean isBlockingMission() {
        return status.isResolvable() && blocking;
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

    /** The courier's actor of a role (COURIER for drivers, AGENCY / SUPPORT for managers, SYSTEM otherwise). */
    public static TntEventActor actorOf(ActorRole role, UUID actorId) {
        if (role == null) {
            return TntEventActor.system();
        }
        return switch (role) {
            case GO_BIKER, FREELANCER_DRIVER, PERMANENT_DRIVER, AGENCY_DRIVER ->
                    TntEventActor.of(TntEventActor.Type.COURIER, actorId);
            case AGENCY_MANAGER, BRANCH_MANAGER, DISPATCHER -> TntEventActor.of(TntEventActor.Type.AGENCY, actorId);
            case SUPPORT_AGENT, ADMIN_TIIBNTICK -> TntEventActor.of(TntEventActor.Type.SUPPORT, actorId);
            case RELAY_POINT_OPERATOR -> TntEventActor.of(TntEventActor.Type.RELAY_OP, actorId);
            case CLIENT -> TntEventActor.of(TntEventActor.Type.SENDER, actorId);
            case RECIPIENT -> TntEventActor.of(TntEventActor.Type.RECIPIENT, actorId);
            case SYSTEM -> TntEventActor.system();
        };
    }

    /** The manager: the delivery's agency, else the TiiBnTick support. */
    public TntEventActor managerActor(UUID actorId) {
        return TntEventActor.of(agencyId != null ? TntEventActor.Type.AGENCY : TntEventActor.Type.SUPPORT, actorId);
    }

    // ── internals ────────────────────────────────────────────────────────────

    /**
     * The only way to change the state: checks row I1–I31 ({@link IncidentStatus#canTransitionTo}), applies the
     * changes and records the event to publish.
     */
    private Incident moveTo(IncidentStatus target, String trigger, TntEventActor actor, Map<String, Object> details,
                            java.util.function.Consumer<IncidentBuilder> changes) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Incident " + id + ": transition " + status + " -> " + target
                    + " is not allowed (spec §1.3)");
        }
        IncidentBuilder b = toBuilder().status(target).eventSequence(eventSequence + 1);
        changes.accept(b);
        Incident next = b.build();
        next.pendingTransitions.add(new IncidentTransition(status, target, null, trigger,
                actor != null ? actor : TntEventActor.system(), details, Instant.now()));
        return next;
    }

    private Incident change(String changeKind, String trigger, TntEventActor actor, Map<String, Object> details) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Incident " + id + " is terminal: " + status);
        }
        Incident next = toBuilder().eventSequence(eventSequence + 1).build();
        next.pendingTransitions.add(new IncidentTransition(status, status, changeKind, trigger,
                actor != null ? actor : TntEventActor.system(), details, Instant.now()));
        return next;
    }

    private void requireStatus(IncidentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Incident " + id + " must be " + expected + ", is " + status);
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);

    /**
     * {@code TNT-INC-<yyMMdd>-<10 hex>}: 40 random bits per day. The former {@code TNT-INC-<6 digits>} drew from one
     * million values under a UNIQUE constraint and collided (insert failure) after about a thousand incidents.
     */
    static String generateReferenceCode(Instant now) {
        byte[] bytes = new byte[5];
        RANDOM.nextBytes(bytes);
        return "TNT-INC-" + DAY.format(now) + "-" + HexFormat.of().withUpperCase().formatHex(bytes);
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
                .compensationImpact(compensationImpact).version(version)
                .responsibleOrgId(responsibleOrgId).responsibleOrgType(responsibleOrgType)
                .trackingCode(trackingCode)
                .blocking(blocking).blockingForcedByCourier(blockingForcedByCourier)
                .deliveryOutcome(deliveryOutcome).responsibleParty(responsibleParty)
                .resolutionNote(resolutionNote).resolutionProposedAt(resolutionProposedAt)
                .eventSequence(eventSequence)
                .pendingTransitions(new ArrayList<>(pendingTransitions));
    }
}
