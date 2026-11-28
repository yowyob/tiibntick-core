package com.yowyob.tiibntick.core.dispute.domain.model;

import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.dispute.application.command.*;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeStateException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Aggregate Root for dispute management in TiiBnTick.
 *
 * <p>A {@code Dispute} represents a formal complaint raised by any party (client,
 * recipient, freelancer, hub operator) regarding a logistic incident (loss, damage,
 * delay, fraud, non-conformity). It orchestrates the complete dispute lifecycle:
 * opening → investigation → mediation → arbitration → compensation → closure.
 *
 * <p>This aggregate enforces all state-machine invariants and produces domain events
 * consumed by tnt-delivery-core (DISPUTED package state), tnt-billing-wallet
 * (compensation processing), tnt-notify-core (notifications), and tnt-actor-core
 * (reputation scoring).
 *
 * <p><b>Platforms:</b> Agency, Go, Link, Point, Freelancer, Market.
 *
 * @author MANFOUO Braun
 */
public class Dispute {

    private final DisputeId id;
    private final String tenantId;
    private final DisputeReference reference;
    private DisputeCause cause;
    private final DisputeCategory category;
    private DisputePriority priority;
    private DisputeStatus status;
    private final String claimantId;
    private final ClaimantType claimantType;
    private final String respondentId;
    private final RespondentType respondentType;
    private final String missionId;
    private final String packageId;
    private final String trackingCode;
    private String description;
    private final LocalDateTime filedAt;
    private LocalDateTime deadline;
    private String assignedMediatorId;
    private DisputeResolution resolution;
    private CompensationDetails compensation;
    private final List<DisputeEvidence> evidences;
    private final List<DisputeEvent> timeline;
    private final List<DisputeComment> comments;
    private final List<EscalationRecord> escalationHistory;
    private DisputeSLAPolicy slaPolicy;
    private int version;

    // ── : FreelancerOrg respondent context ────────────────────────────
    /**
     * UUID of the respondent organization (FreelancerOrg or Agency).
     * Populated when {@code respondentType} is FREELANCER_ORG, AGENCY, HUB_POINT, or LINK_NETWORK.
     * References tnt-organization-core UUID — pure integration key (no physical FK).
     */
    private final String respondentOrgId;

    /**
     * UUID of the SUB_DELIVERER actor implicated in this dispute.
     * Null when the OWNER executes the mission directly.
     * References tnt-actor-core UUID — pure integration key.
     */
    private final String impliedSubDelivererId;

    /**
     * Whether a SUB_DELIVERER from the FreelancerOrg is involved in this dispute.
     * Used to route compensation split if applicable.
     */
    private final Boolean subDelivererInvolved;

    // ── Lot L4: lifecycle published on tnt.dispute.events ─────────────────────
    /** Party at fault set by the ruling (pays the return / new delivery, [B.6]). */
    private DisputeResponsibleParty responsibleParty;
    /** Incident escalated into this dispute (LT1 automatic opening), {@code null} otherwise. */
    private String incidentId;
    /** When the dispute reached a terminal state. */
    private LocalDateTime closedAt;
    /** {@code aggregateVersion} of the last event published for this dispute. */
    private long eventSequence;
    /** Changes not yet published (cleared by the repository once published). */
    private final List<DisputeTransition> pendingTransitions = new ArrayList<>();

    private Dispute(
            final DisputeId id,
            final String tenantId,
            final DisputeReference reference,
            final DisputeCause cause,
            final DisputeCategory category,
            final DisputePriority priority,
            final DisputeStatus status,
            final String claimantId,
            final ClaimantType claimantType,
            final String respondentId,
            final RespondentType respondentType,
            final String missionId,
            final String packageId,
            final String trackingCode,
            final String description,
            final LocalDateTime filedAt,
            final LocalDateTime deadline,
            final String assignedMediatorId,
            final DisputeResolution resolution,
            final CompensationDetails compensation,
            final List<DisputeEvidence> evidences,
            final List<DisputeEvent> timeline,
            final List<DisputeComment> comments,
            final List<EscalationRecord> escalationHistory,
            final DisputeSLAPolicy slaPolicy,
            final int version,
            final String respondentOrgId,
            final String impliedSubDelivererId,
            final Boolean subDelivererInvolved) {
        this.id = Objects.requireNonNull(id, "DisputeId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.reference = Objects.requireNonNull(reference, "DisputeReference must not be null");
        this.cause = Objects.requireNonNull(cause, "DisputeCause must not be null");
        this.category = Objects.requireNonNull(category, "DisputeCategory must not be null");
        this.priority = Objects.requireNonNull(priority, "DisputePriority must not be null");
        this.status = Objects.requireNonNull(status, "DisputeStatus must not be null");
        this.claimantId = Objects.requireNonNull(claimantId, "claimantId must not be null");
        this.claimantType = Objects.requireNonNull(claimantType, "claimantType must not be null");
        this.respondentId = Objects.requireNonNull(respondentId, "respondentId must not be null");
        this.respondentType = Objects.requireNonNull(respondentType, "respondentType must not be null");
        this.missionId = missionId;
        this.packageId = packageId;
        this.trackingCode = trackingCode;
        this.description = description;
        this.filedAt = Objects.requireNonNull(filedAt, "filedAt must not be null");
        this.deadline = deadline;
        this.assignedMediatorId = assignedMediatorId;
        this.resolution = resolution;
        this.compensation = compensation;
        this.evidences = new ArrayList<>(evidences != null ? evidences : List.of());
        this.timeline = new ArrayList<>(timeline != null ? timeline : List.of());
        this.comments = new ArrayList<>(comments != null ? comments : List.of());
        this.escalationHistory = new ArrayList<>(escalationHistory != null ? escalationHistory : List.of());
        this.slaPolicy = Objects.requireNonNull(slaPolicy, "slaPolicy must not be null");
        this.version = version;
        this.respondentOrgId = respondentOrgId;
        this.impliedSubDelivererId = impliedSubDelivererId;
        this.subDelivererInvolved = subDelivererInvolved;
    }

    // =========================================================================
    // FACTORY — Open a new dispute
    // =========================================================================

    /**
     * Opens a new dispute from a command. This is the only entry point for creating disputes.
     * Sets initial SLA policy, records timeline event, and the {@code DisputeOpen} transition published on
     * {@code tnt.dispute.events}.
     *
     * <p>The {@link DisputeReference} is generated by the caller (via
     * {@code IDisputeReferenceGenerator.nextReference()}, backed by a PostgreSQL sequence —
     * see Chantier D · Audit n°6 · S1) rather than by this factory, since obtaining a
     * globally unique reference is an I/O operation and this method must stay synchronous.
     *
     * @param cmd       the command containing all required opening information
     * @param reference a fresh, globally unique reference obtained beforehand
     * @return a new {@code Dispute} in {@link DisputeStatus#OPEN} state
     */
    public static Dispute open(final OpenDisputeCommand cmd, final DisputeReference reference) {
        Objects.requireNonNull(cmd, "OpenDisputeCommand must not be null");
        Objects.requireNonNull(reference, "DisputeReference must not be null");

        final DisputeId id = DisputeId.generate();
        final DisputeSLAPolicy slaPolicy = DisputeSLAPolicy.forPriority(cmd.priority());
        final LocalDateTime now = LocalDateTime.now();

        final Dispute dispute = new Dispute(
                id, cmd.tenantId(), reference, cmd.cause(), cmd.category(),
                cmd.priority(), DisputeStatus.OPEN,
                cmd.claimantId(), cmd.claimantType(),
                cmd.respondentId(), cmd.respondentType(),
                cmd.missionId(), cmd.packageId(), cmd.trackingCode(),
                cmd.description(), now, slaPolicy.resolutionDeadline(now),
                null, null, null,
                List.of(), List.of(), List.of(), List.of(), slaPolicy, 0,
                cmd.respondentOrgId(), cmd.impliedSubDelivererId(), cmd.subDelivererInvolved());

        dispute.incidentId = cmd.incidentId();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("openedFromIncidentId", cmd.incidentId());
        dispute.pendingTransitions.add(new DisputeTransition(null, DisputeStatus.OPEN, null,
                cmd.incidentId() != null ? "INCIDENT_ESCALATED_TO_DISPUTE" : "OPEN_DISPUTE",
                actorOf(cmd.claimantType(), cmd.claimantId()), details, Instant.now()));
        dispute.eventSequence = 1;
        dispute.recordEvent(DisputeEventType.OPENED, "Dispute filed by claimant", cmd.claimantId(), ActorType.USER);

        return dispute;
    }

    // =========================================================================
    // COMMANDS — State transitions (strict machine enforcement)
    // =========================================================================

    /**
     * Assigns a mediator to this dispute, transitioning it from OPEN to UNDER_INVESTIGATION.
     *
     * @param mediatorId the actor ID of the mediator being assigned
     * @throws DisputeStateException if the dispute is not in OPEN state
     */
    public void assignMediator(final String mediatorId) {
        requireStatus(DisputeStatus.OPEN, "assignMediator");
        Objects.requireNonNull(mediatorId, "mediatorId must not be null");

        this.assignedMediatorId = mediatorId;
        moveTo("LT2", DisputeStatus.UNDER_INVESTIGATION, "ASSIGN_MEDIATOR", support(mediatorId), null);

        recordEvent(DisputeEventType.MEDIATOR_ASSIGNED, "Mediator %s assigned".formatted(mediatorId), mediatorId, ActorType.MEDIATOR);
    }

    /**
     * Adds a piece of evidence to this dispute. Allowed from UNDER_INVESTIGATION or AWAITING_EVIDENCE.
     * If in AWAITING_EVIDENCE, transitions back to UNDER_INVESTIGATION upon evidence receipt.
     *
     * @param evidence the evidence to add
     * @throws DisputeStateException if the current state does not allow evidence submission
     */
    public void addEvidence(final DisputeEvidence evidence) {
        requireOneOfStatuses(List.of(DisputeStatus.UNDER_INVESTIGATION, DisputeStatus.AWAITING_EVIDENCE), "addEvidence");
        Objects.requireNonNull(evidence, "evidence must not be null");

        this.evidences.add(evidence);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("evidenceId", evidence.getId().getValue());
        details.put("evidenceType", evidence.getType() != null ? evidence.getType().name() : null);
        TntEventActor submitter = partyActor(evidence.getSubmittedBy());
        if (this.status == DisputeStatus.AWAITING_EVIDENCE) {
            moveTo("LT4", DisputeStatus.UNDER_INVESTIGATION, "EVIDENCE_SUBMITTED", submitter, details);
        } else {
            change("EVIDENCE_ADDED", "EVIDENCE_SUBMITTED", submitter, details);
        }

        recordEvent(DisputeEventType.EVIDENCE_SUBMITTED,
                "Evidence submitted: %s by %s".formatted(evidence.getType(), evidence.getSubmittedBy()),
                evidence.getSubmittedBy(), ActorType.USER);
    }

    /**
     * Requests additional evidence from a party, transitioning to AWAITING_EVIDENCE.
     *
     * @param requestedFrom  actor ID of the party expected to submit evidence
     * @param evidenceDeadline deadline by which evidence must be submitted
     * @throws DisputeStateException if the dispute is not UNDER_INVESTIGATION
     */
    public void requestAdditionalEvidence(final String requestedFrom, final LocalDateTime evidenceDeadline) {
        requireStatus(DisputeStatus.UNDER_INVESTIGATION, "requestAdditionalEvidence");
        Objects.requireNonNull(requestedFrom, "requestedFrom must not be null");
        Objects.requireNonNull(evidenceDeadline, "evidenceDeadline must not be null");

        this.deadline = evidenceDeadline;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("requestedFrom", requestedFrom);
        details.put("deadline", evidenceDeadline.toString());
        moveTo("LT3", DisputeStatus.AWAITING_EVIDENCE, "REQUEST_EVIDENCE", support(assignedMediatorId), details);

        recordEvent(DisputeEventType.EVIDENCE_REQUESTED,
                "Additional evidence requested from %s. Deadline: %s".formatted(requestedFrom, evidenceDeadline),
                assignedMediatorId, ActorType.MEDIATOR);
    }

    /**
     * Starts formal mediation between claimant and respondent.
     * Transitions from UNDER_INVESTIGATION to MEDIATION_IN_PROGRESS.
     *
     * @throws DisputeStateException if the dispute is not UNDER_INVESTIGATION
     */
    public void startMediation() {
        requireStatus(DisputeStatus.UNDER_INVESTIGATION, "startMediation");
        moveTo("LT5", DisputeStatus.MEDIATION_IN_PROGRESS, "START_MEDIATION", support(assignedMediatorId), null);
        recordEvent(DisputeEventType.MEDIATION_STARTED, "Mediation session started", assignedMediatorId, ActorType.MEDIATOR);
    }

    /**
     * Issues a ruling on the dispute. Handles multiple resolution types:
     * compensation granted → PENDING_COMPENSATION, otherwise → CLOSED_RESOLVED.
     *
     * @param cmd the ruling command containing resolution type and optional compensation details
     * @throws DisputeStateException if ruling is not allowed from the current state
     */
    public void rule(final RuleDisputeCommand cmd) {
        requireOneOfStatuses(List.of(DisputeStatus.MEDIATION_IN_PROGRESS, DisputeStatus.PENDING_ARBITRATION), "rule");
        Objects.requireNonNull(cmd, "RuleDisputeCommand must not be null");

        ResolutionType type = cmd.resolutionType();
        boolean paid = cmd.compensationRequired() && cmd.compensation() != null
                && cmd.compensation().getAmount() != null && cmd.compensation().getAmount().signum() > 0;
        if (type.grantsCompensation() && !paid) {
            throw new DisputeStateException("Resolution " + type + " requires a compensation with an amount > 0 ("
                    + "row LT7) for dispute " + id);
        }
        if (!type.grantsCompensation() && cmd.compensationRequired()) {
            throw new DisputeStateException("Resolution " + type + " closes the dispute without compensation (row LT8)"
                    + " for dispute " + id);
        }
        this.resolution = DisputeResolution.of(type, cmd.compensationRequired(), cmd.ruledBy(), cmd.summary());
        this.responsibleParty = cmd.responsibleParty();

        Map<String, Object> details = new LinkedHashMap<>();
        if (paid) {
            this.compensation = cmd.compensation();
            details.put("amount", cmd.compensation().getAmount().toPlainString());
            details.put("currency", cmd.compensation().getCurrency());
            details.put("paymentRef", null);
            moveTo("LT7", DisputeStatus.PENDING_COMPENSATION, "RULE_DISPUTE", support(cmd.ruledBy()), details);
        } else {
            details.put("resolutionType", type.name());
            details.put("closureType", null);
            moveTo("LT8", DisputeStatus.CLOSED_RESOLVED, "RULE_DISPUTE", support(cmd.ruledBy()), details);
        }

        recordEvent(DisputeEventType.RULING_ISSUED,
                "Ruling issued: %s".formatted(cmd.resolutionType()), cmd.ruledBy(), ActorType.MEDIATOR);
    }

    /**
     * Escalates the dispute to arbitration, transitioning to PENDING_ARBITRATION.
     *
     * @param cmd the escalation command
     * @throws DisputeStateException if escalation is not allowed from the current state
     */
    public void escalate(final EscalateDisputeCommand cmd) {
        requireOneOfStatuses(
                List.of(DisputeStatus.UNDER_INVESTIGATION, DisputeStatus.MEDIATION_IN_PROGRESS), "escalate");
        Objects.requireNonNull(cmd, "EscalateDisputeCommand must not be null");

        final DisputeStatus from = this.status;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", cmd.reason());
        moveTo("LT6", DisputeStatus.PENDING_ARBITRATION, "ESCALATE_DISPUTE", support(cmd.escalatedBy()), details);
        this.escalationHistory.add(EscalationRecord.of(
                cmd.escalatedBy(), cmd.reason(), from, DisputeStatus.PENDING_ARBITRATION, cmd.assignedArbitratorId()));

        recordEvent(DisputeEventType.ESCALATED,
                "Dispute escalated. Reason: %s".formatted(cmd.reason()), cmd.escalatedBy(), ActorType.MEDIATOR);
    }

    /**
     * Processes the approved compensation payment, transitioning to COMPENSATED.
     *
     * @param paymentReference the transaction reference from the payment gateway
     * @throws DisputeStateException if the dispute is not in PENDING_COMPENSATION state
     */
    public void processCompensation(final String paymentReference) {
        requireStatus(DisputeStatus.PENDING_COMPENSATION, "processCompensation");
        Objects.requireNonNull(paymentReference, "paymentReference must not be null");
        Objects.requireNonNull(compensation, "compensation must have been set before processing");

        this.compensation = this.compensation.markAsPaid(paymentReference);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("amount", compensation.getAmount() != null ? compensation.getAmount().toPlainString() : null);
        details.put("currency", compensation.getCurrency());
        details.put("paymentRef", paymentReference);
        moveTo("LT9", DisputeStatus.COMPENSATED, "COMPENSATION_PAID", TntEventActor.system(), details);

        recordEvent(DisputeEventType.COMPENSATION_PAID,
                "Compensation paid. Ref: %s".formatted(paymentReference), "SYSTEM", ActorType.SYSTEM);
    }

    /**
     * Closes the dispute with an explicit closure type (administrative or system closure).
     *
     * @param cmd the close command
     * @throws DisputeStateException if the dispute is already terminal
     */
    public void close(final CloseDisputeCommand cmd) {
        requireNonTerminal("close");
        Objects.requireNonNull(cmd, "CloseDisputeCommand must not be null");
        // Row LT12 (correction C10): COMPENSATED only through LT9, CLOSED_RESOLVED through LT8/LT12, CLOSED_EXPIRED
        // through LT11, CLOSED_WITHDRAWN through LT10
        if (cmd.closureType() != ClosureType.ADMINISTRATIVE_CLOSURE) {
            throw new DisputeStateException("Only an ADMINISTRATIVE_CLOSURE closes a dispute directly (got "
                    + cmd.closureType() + ") for dispute " + id);
        }
        if (cmd.summary() == null || cmd.summary().isBlank()) {
            throw new DisputeStateException("An administrative closure requires a reason for dispute " + id);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("resolutionType", resolution != null ? resolution.getType().name() : null);
        details.put("closureType", ClosureType.ADMINISTRATIVE_CLOSURE.name());
        moveTo("LT12", DisputeStatus.CLOSED_RESOLVED, "ADMINISTRATIVE_CLOSURE", support(cmd.closedBy()), details);

        recordEvent(DisputeEventType.CLOSED, "Dispute closed: %s".formatted(cmd.closureType()), cmd.closedBy(), ActorType.ADMIN);
    }

    /**
     * Withdraws the dispute voluntarily by the claimant.
     *
     * @param claimantId the ID of the claimant withdrawing the dispute
     * @throws DisputeStateException if withdrawal is not permitted from the current state
     */
    public void withdraw(final String claimantId) {
        if (!status.allowsWithdrawal()) {
            throw new DisputeStateException(
                    "Withdrawal is not permitted from state: " + status.name());
        }
        Objects.requireNonNull(claimantId, "claimantId must not be null");
        if (!claimantId.equals(this.claimantId)) {
            // row LT10: only the claimant withdraws his dispute
            throw new com.yowyob.tiibntick.core.dispute.domain.exception.DisputeAccessDeniedException(
                    claimantId, "withdraw", id.getValue());
        }

        moveTo("LT10", DisputeStatus.CLOSED_WITHDRAWN, "WITHDRAW_DISPUTE", actorOf(claimantType, claimantId), null);
        recordEvent(DisputeEventType.WITHDRAWN, "Dispute withdrawn by claimant", claimantId, ActorType.USER);
    }

    /**
     * Adds a comment to the dispute thread.
     *
     * @param comment the comment to post
     * @throws DisputeStateException if the dispute is already in a terminal state
     */
    public void addComment(final DisputeComment comment) {
        requireNonTerminal("addComment");
        Objects.requireNonNull(comment, "comment must not be null");
        this.comments.add(comment);
        recordEvent(DisputeEventType.COMMENT_ADDED, "Comment added by %s".formatted(comment.getAuthorId()),
                comment.getAuthorId(), ActorType.USER);
    }

    /**
     * Row LT11: the deadline passed while the dispute waited (open, under investigation, or awaiting evidence).
     *
     */
    public void expire() {
        moveTo("LT11", DisputeStatus.CLOSED_EXPIRED, "DEADLINE_EXCEEDED", TntEventActor.system(), null);
        recordEvent(DisputeEventType.CLOSED, "Dispute closed: EXPIRED", "SYSTEM", ActorType.SYSTEM);
    }

    /**
     * Marks the SLA as breached and records a SLA_BREACHED timeline event.
     * Called by the SLA scheduler. Does not change the dispute status.
     *
     * @param breachDescription a human-readable description of which SLA was breached
     */
    public void markSlaBreached(final String breachDescription) {
        recordEvent(DisputeEventType.SLA_BREACHED, breachDescription, "SYSTEM", ActorType.SYSTEM);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /** Applies row {@code rowId} of {@link DisputeStateMachine} and stacks the event to publish. */
    private void moveTo(String rowId, DisputeStatus target, String trigger, TntEventActor actor,
                        Map<String, Object> details) {
        DisputeStateMachine.Row row = DisputeStateMachine.row(rowId);
        if (row.to() != target || !row.from().contains(status)) {
            throw new DisputeStateException("Transition %s -> %s (row %s) is not allowed for dispute %s"
                    .formatted(status, target, rowId, id));
        }
        DisputeStatus from = this.status;
        this.status = target;
        if (target.isTerminal()) {
            this.closedAt = LocalDateTime.now();
        }
        this.eventSequence++;
        pendingTransitions.add(new DisputeTransition(from, target, null, trigger, actor, details, Instant.now()));
    }

    /** A change without transition ({@code EVIDENCE_ADDED}). */
    private void change(String kind, String trigger, TntEventActor actor, Map<String, Object> details) {
        this.eventSequence++;
        pendingTransitions.add(new DisputeTransition(status, status, kind, trigger, actor, details, Instant.now()));
    }

    /** Actor of the claimant (spec §3.3 actor types). */
    public static TntEventActor actorOf(ClaimantType type, String id) {
        TntEventActor.Type t = switch (type) {
            case CLIENT -> TntEventActor.Type.SENDER;
            case RECIPIENT -> TntEventActor.Type.RECIPIENT;
            case FREELANCER, PERMANENT_DELIVERER -> TntEventActor.Type.COURIER;
            case HUB_OPERATOR -> TntEventActor.Type.RELAY_OP;
            case AGENCY_MANAGER -> TntEventActor.Type.AGENCY;
            case SYSTEM -> TntEventActor.Type.SYSTEM;
        };
        return t == TntEventActor.Type.SYSTEM ? TntEventActor.system() : TntEventActor.of(t, uuid(id));
    }

    /** The claimant when {@code id} is the claimant, else the support (a mediator, a respondent). */
    private TntEventActor partyActor(String id) {
        return id != null && id.equals(claimantId) ? actorOf(claimantType, claimantId) : support(id);
    }

    private static TntEventActor support(String id) {
        return TntEventActor.of(TntEventActor.Type.SUPPORT, uuid(id));
    }

    private static UUID uuid(String id) {
        try {
            return id == null || id.isBlank() ? null : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void requireStatus(final DisputeStatus expected, final String operation) {
        if (this.status != expected) {
            throw new DisputeStateException(
                    "Operation '%s' requires status [%s] but current status is [%s] for dispute %s"
                            .formatted(operation, expected, this.status, id));
        }
    }

    private void requireOneOfStatuses(final List<DisputeStatus> allowed, final String operation) {
        if (!allowed.contains(this.status)) {
            throw new DisputeStateException(
                    "Operation '%s' requires one of %s but current status is [%s] for dispute %s"
                            .formatted(operation, allowed, this.status, id));
        }
    }

    private void requireNonTerminal(final String operation) {
        if (this.status.isTerminal()) {
            throw new DisputeStateException(
                    "Operation '%s' is not allowed on a terminal dispute [%s]".formatted(operation, id));
        }
    }

    private void recordEvent(
            final DisputeEventType type,
            final String description,
            final String performedBy,
            final ActorType performedByType) {
        this.timeline.add(DisputeEvent.of(id, type, description, performedBy, performedByType));
    }

    // =========================================================================
    // RECONSTITUTION FACTORY (from persistence)
    // =========================================================================

    /**
     * Reconstitutes a {@code Dispute} aggregate from persisted state.
     * Must only be called by the repository adapter — never directly by application code.
     */
    public static Dispute reconstitute(
            final DisputeId id,
            final String tenantId,
            final DisputeReference reference,
            final DisputeCause cause,
            final DisputeCategory category,
            final DisputePriority priority,
            final DisputeStatus status,
            final String claimantId,
            final ClaimantType claimantType,
            final String respondentId,
            final RespondentType respondentType,
            final String missionId,
            final String packageId,
            final String trackingCode,
            final String description,
            final LocalDateTime filedAt,
            final LocalDateTime deadline,
            final String assignedMediatorId,
            final DisputeResolution resolution,
            final CompensationDetails compensation,
            final List<DisputeEvidence> evidences,
            final List<DisputeEvent> timeline,
            final List<DisputeComment> comments,
            final List<EscalationRecord> escalationHistory,
            final DisputeSLAPolicy slaPolicy,
            final int version) {
        return reconstituteFull(id, tenantId, reference, cause, category, priority, status,
                claimantId, claimantType, respondentId, respondentType, missionId, packageId,
                trackingCode, description, filedAt, deadline, assignedMediatorId, resolution,
                compensation, evidences, timeline, comments, escalationHistory, slaPolicy, version,
                null, null, null);
    }

    /**
     * Full reconstitution factory including  FreelancerOrg fields.
     * Used by persistence mapper when loading disputes with respondentOrgId data.
     */
    public static Dispute reconstituteFull(
            final DisputeId id, final String tenantId, final DisputeReference reference,
            final DisputeCause cause, final DisputeCategory category, final DisputePriority priority,
            final DisputeStatus status, final String claimantId, final ClaimantType claimantType,
            final String respondentId, final RespondentType respondentType,
            final String missionId, final String packageId, final String trackingCode,
            final String description, final LocalDateTime filedAt, final LocalDateTime deadline,
            final String assignedMediatorId, final DisputeResolution resolution,
            final CompensationDetails compensation, final List<DisputeEvidence> evidences,
            final List<DisputeEvent> timeline, final List<DisputeComment> comments,
            final List<EscalationRecord> escalationHistory,
            final DisputeSLAPolicy slaPolicy, final int version,
            final String respondentOrgId, final String impliedSubDelivererId,
            final Boolean subDelivererInvolved) {
        return new Dispute(id, tenantId, reference, cause, category, priority, status,
                claimantId, claimantType, respondentId, respondentType, missionId, packageId,
                trackingCode, description, filedAt, deadline, assignedMediatorId, resolution,
                compensation, evidences, timeline, comments, escalationHistory, slaPolicy, version,
                respondentOrgId, impliedSubDelivererId, subDelivererInvolved);
    }

    // =========================================================================
    // DOMAIN EVENTS
    // =========================================================================

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    public DisputeId getId() { return id; }
    public String getTenantId() { return tenantId; }
    public DisputeReference getReference() { return reference; }
    public DisputeCause getCause() { return cause; }
    public DisputeCategory getCategory() { return category; }
    public DisputePriority getPriority() { return priority; }
    public DisputeStatus getStatus() { return status; }
    public String getClaimantId() { return claimantId; }
    public ClaimantType getClaimantType() { return claimantType; }
    public String getRespondentId() { return respondentId; }
    public RespondentType getRespondentType() { return respondentType; }
    public String getMissionId() { return missionId; }
    public String getPackageId() { return packageId; }
    public String getTrackingCode() { return trackingCode; }
    public String getDescription() { return description; }
    public LocalDateTime getFiledAt() { return filedAt; }
    public LocalDateTime getDeadline() { return deadline; }
    public String getAssignedMediatorId() { return assignedMediatorId; }
    public DisputeResolution getResolution() { return resolution; }
    public CompensationDetails getCompensation() { return compensation; }
    public List<DisputeEvidence> getEvidences() { return Collections.unmodifiableList(evidences); }
    public List<DisputeEvent> getTimeline() { return Collections.unmodifiableList(timeline); }
    public List<DisputeComment> getComments() { return Collections.unmodifiableList(comments); }
    public List<EscalationRecord> getEscalationHistory() { return Collections.unmodifiableList(escalationHistory); }
    public DisputeSLAPolicy getSlaPolicy() { return slaPolicy; }
    public int getVersion() { return version; }
    //  getters
    public String getRespondentOrgId() { return respondentOrgId; }
    public String getImpliedSubDelivererId() { return impliedSubDelivererId; }
    public Boolean getSubDelivererInvolved() { return subDelivererInvolved; }
    public DisputeResponsibleParty getResponsibleParty() { return responsibleParty; }
    public String getIncidentId() { return incidentId; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public long getEventSequence() { return eventSequence; }
    public List<DisputeTransition> getPendingTransitions() { return Collections.unmodifiableList(pendingTransitions); }

    /** Forgets the changes once published (repository). */
    public void clearPendingTransitions() {
        pendingTransitions.clear();
    }

    /** Lot L4 state read back from persistence (repository adapter only). */
    public Dispute restoreLifecycle(DisputeResponsibleParty responsibleParty, String incidentId,
                                    LocalDateTime closedAt, long eventSequence) {
        this.responsibleParty = responsibleParty;
        this.incidentId = incidentId;
        this.closedAt = closedAt;
        this.eventSequence = eventSequence;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Dispute that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Dispute{id=%s, ref=%s, status=%s, tenant=%s}".formatted(id, reference, status, tenantId);
    }
}
