package com.yowyob.tiibntick.core.dispute.domain.model;

import com.yowyob.tiibntick.core.dispute.application.command.*;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.event.*;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeStateException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    // Uncommitted domain events — cleared after publishing
    private final List<Object> domainEvents = new ArrayList<>();

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
     * Generates the reference, sets initial SLA policy, records timeline event,
     * and produces a {@link DisputeOpened} domain event.
     *
     * @param cmd the command containing all required opening information
     * @return a new {@code Dispute} in {@link DisputeStatus#OPEN} state
     */
    public static Dispute open(final OpenDisputeCommand cmd) {
        Objects.requireNonNull(cmd, "OpenDisputeCommand must not be null");

        final DisputeId id = DisputeId.generate();
        final DisputeReference reference = DisputeReference.generate();
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

        dispute.recordEvent(DisputeEventType.OPENED, "Dispute filed by claimant", cmd.claimantId(), ActorType.USER);
        dispute.raiseDomainEvent(new DisputeOpened(
                id, cmd.tenantId(), reference.getValue(), cmd.cause(), cmd.priority(),
                cmd.claimantId(), cmd.missionId(), cmd.packageId(), cmd.trackingCode(), now));

        return dispute;
    }

    // =========================================================================
    // COMMANDS — State transitions (strict machine enforcement)
    // =========================================================================

    /**
     * Assigns a mediator to this dispute, transitioning it from OPEN to UNDER_INVESTIGATION.
     *
     * @param mediatorId the actor ID of the mediator being assigned
     * @return the {@link MediatorAssigned} domain event for publishing
     * @throws DisputeStateException if the dispute is not in OPEN state
     */
    public MediatorAssigned assignMediator(final String mediatorId) {
        requireStatus(DisputeStatus.OPEN, "assignMediator");
        Objects.requireNonNull(mediatorId, "mediatorId must not be null");

        this.assignedMediatorId = mediatorId;
        this.status = DisputeStatus.UNDER_INVESTIGATION;

        recordEvent(DisputeEventType.MEDIATOR_ASSIGNED, "Mediator %s assigned".formatted(mediatorId), mediatorId, ActorType.MEDIATOR);

        final MediatorAssigned event = new MediatorAssigned(id, tenantId, mediatorId, LocalDateTime.now());
        raiseDomainEvent(event);
        return event;
    }

    /**
     * Adds a piece of evidence to this dispute. Allowed from UNDER_INVESTIGATION or AWAITING_EVIDENCE.
     * If in AWAITING_EVIDENCE, transitions back to UNDER_INVESTIGATION upon evidence receipt.
     *
     * @param evidence the evidence to add
     * @return the {@link EvidenceSubmitted} domain event
     * @throws DisputeStateException if the current state does not allow evidence submission
     */
    public EvidenceSubmitted addEvidence(final DisputeEvidence evidence) {
        requireOneOfStatuses(List.of(DisputeStatus.UNDER_INVESTIGATION, DisputeStatus.AWAITING_EVIDENCE), "addEvidence");
        Objects.requireNonNull(evidence, "evidence must not be null");

        this.evidences.add(evidence);
        if (this.status == DisputeStatus.AWAITING_EVIDENCE) {
            this.status = DisputeStatus.UNDER_INVESTIGATION;
        }

        recordEvent(DisputeEventType.EVIDENCE_SUBMITTED,
                "Evidence submitted: %s by %s".formatted(evidence.getType(), evidence.getSubmittedBy()),
                evidence.getSubmittedBy(), ActorType.USER);

        final EvidenceSubmitted event = new EvidenceSubmitted(
                id, tenantId, evidence.getId().getValue(), evidence.getType(),
                evidence.getSubmittedBy(), LocalDateTime.now());
        raiseDomainEvent(event);
        return event;
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

        this.status = DisputeStatus.AWAITING_EVIDENCE;
        this.deadline = evidenceDeadline;

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
        this.status = DisputeStatus.MEDIATION_IN_PROGRESS;
        recordEvent(DisputeEventType.MEDIATION_STARTED, "Mediation session started", assignedMediatorId, ActorType.MEDIATOR);
    }

    /**
     * Issues a ruling on the dispute. Handles multiple resolution types:
     * compensation granted → PENDING_COMPENSATION, otherwise → CLOSED_RESOLVED.
     *
     * @param cmd the ruling command containing resolution type and optional compensation details
     * @return the {@link DisputeRuled} domain event
     * @throws DisputeStateException if ruling is not allowed from the current state
     */
    public DisputeRuled rule(final RuleDisputeCommand cmd) {
        requireOneOfStatuses(List.of(DisputeStatus.MEDIATION_IN_PROGRESS, DisputeStatus.PENDING_ARBITRATION), "rule");
        Objects.requireNonNull(cmd, "RuleDisputeCommand must not be null");

        this.resolution = DisputeResolution.of(cmd.resolutionType(), cmd.compensationRequired(), cmd.ruledBy(), cmd.summary());

        if (cmd.compensationRequired() && cmd.compensation() != null) {
            this.compensation = cmd.compensation();
            this.status = DisputeStatus.PENDING_COMPENSATION;
        } else {
            this.status = DisputeStatus.CLOSED_RESOLVED;
        }

        recordEvent(DisputeEventType.RULING_ISSUED,
                "Ruling issued: %s".formatted(cmd.resolutionType()), cmd.ruledBy(), ActorType.MEDIATOR);

        final DisputeRuled event = new DisputeRuled(
                id, tenantId, cmd.resolutionType(), cmd.compensationRequired(), cmd.ruledBy(), LocalDateTime.now());
        raiseDomainEvent(event);
        return event;
    }

    /**
     * Escalates the dispute to arbitration, transitioning to PENDING_ARBITRATION.
     *
     * @param cmd the escalation command
     * @return the {@link DisputeEscalated} domain event
     * @throws DisputeStateException if escalation is not allowed from the current state
     */
    public DisputeEscalated escalate(final EscalateDisputeCommand cmd) {
        requireOneOfStatuses(
                List.of(DisputeStatus.UNDER_INVESTIGATION, DisputeStatus.MEDIATION_IN_PROGRESS), "escalate");
        Objects.requireNonNull(cmd, "EscalateDisputeCommand must not be null");

        final DisputeStatus from = this.status;
        this.status = DisputeStatus.PENDING_ARBITRATION;
        this.escalationHistory.add(EscalationRecord.of(
                cmd.escalatedBy(), cmd.reason(), from, DisputeStatus.PENDING_ARBITRATION, cmd.assignedArbitratorId()));

        recordEvent(DisputeEventType.ESCALATED,
                "Dispute escalated. Reason: %s".formatted(cmd.reason()), cmd.escalatedBy(), ActorType.MEDIATOR);

        final DisputeEscalated event = new DisputeEscalated(id, tenantId, from, cmd.reason(), cmd.escalatedBy(), LocalDateTime.now());
        raiseDomainEvent(event);
        return event;
    }

    /**
     * Processes the approved compensation payment, transitioning to COMPENSATED.
     *
     * @param paymentReference the transaction reference from the payment gateway
     * @return the {@link CompensationProcessed} domain event
     * @throws DisputeStateException if the dispute is not in PENDING_COMPENSATION state
     */
    public CompensationProcessed processCompensation(final String paymentReference) {
        requireStatus(DisputeStatus.PENDING_COMPENSATION, "processCompensation");
        Objects.requireNonNull(paymentReference, "paymentReference must not be null");
        Objects.requireNonNull(compensation, "compensation must have been set before processing");

        this.compensation = this.compensation.markAsPaid(paymentReference);
        this.status = DisputeStatus.COMPENSATED;

        recordEvent(DisputeEventType.COMPENSATION_PAID,
                "Compensation paid. Ref: %s".formatted(paymentReference), "SYSTEM", ActorType.SYSTEM);

        final CompensationProcessed event = new CompensationProcessed(
                id, tenantId, compensation.getAmount(), compensation.getCurrency(),
                compensation.getMethod(), compensation.getBeneficiaryId(), LocalDateTime.now());
        raiseDomainEvent(event);
        return event;
    }

    /**
     * Closes the dispute with an explicit closure type (administrative or system closure).
     *
     * @param cmd the close command
     * @return the {@link DisputeClosed} domain event
     * @throws DisputeStateException if the dispute is already terminal
     */
    public DisputeClosed close(final CloseDisputeCommand cmd) {
        requireNonTerminal("close");
        Objects.requireNonNull(cmd, "CloseDisputeCommand must not be null");

        this.status = switch (cmd.closureType()) {
            case EXPIRED -> DisputeStatus.CLOSED_EXPIRED;
            case RESOLVED_WITH_COMPENSATION -> DisputeStatus.COMPENSATED;
            case RESOLVED_WITHOUT_COMPENSATION -> DisputeStatus.CLOSED_RESOLVED;
            case WITHDRAWN_BY_CLAIMANT -> DisputeStatus.CLOSED_WITHDRAWN;
            case ADMINISTRATIVE_CLOSURE -> DisputeStatus.CLOSED_RESOLVED;
        };

        recordEvent(DisputeEventType.CLOSED, "Dispute closed: %s".formatted(cmd.closureType()), cmd.closedBy(), ActorType.ADMIN);

        final DisputeClosed event = new DisputeClosed(id, tenantId, cmd.closureType(), cmd.summary(), LocalDateTime.now());
        raiseDomainEvent(event);
        return event;
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

        this.status = DisputeStatus.CLOSED_WITHDRAWN;
        recordEvent(DisputeEventType.WITHDRAWN, "Dispute withdrawn by claimant", claimantId, ActorType.USER);
        raiseDomainEvent(new DisputeClosed(id, tenantId, ClosureType.WITHDRAWN_BY_CLAIMANT, "Claimant withdrew the dispute", LocalDateTime.now()));
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

    private void raiseDomainEvent(final Object event) {
        this.domainEvents.add(event);
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

    /**
     * Returns the uncommitted domain events produced by this aggregate
     * since the last time events were cleared.
     *
     * @return an unmodifiable view of pending domain events
     */
    public List<Object> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    /**
     * Clears all uncommitted domain events after they have been published.
     * Must be called by the repository adapter after persisting and publishing.
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

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
