package com.yowyob.tiibntick.core.dispute.application.service;

import com.yowyob.tiibntick.common.kafka.TntEventActor;
import com.yowyob.tiibntick.core.dispute.application.command.*;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeCommandUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeSystemUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.*;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeCause;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus;
import com.yowyob.tiibntick.core.dispute.domain.enums.ResolutionType;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeCompensationException;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeNotFoundException;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeStateException;
import com.yowyob.tiibntick.core.dispute.domain.model.*;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Application service implementing all dispute write operations (spec §1.4, lot L4).
 *
 * <p>Each change loads the aggregate, applies one row of the transition table (LT1–LT12), applies the parcel effect
 * through {@link IDisputeParcelPort} (delivery-core, same transaction: P12 at opening, P13–P17 at the outcome) and saves;
 * the repository writes the {@code tnt.dispute.events} outbox rows in that transaction (lot L8: the legacy per-type
 * topics are gone). The notifications are best effort after the change.
 *
 * <p>The compensation (LT7 → LT9) is paid synchronously through {@link IDisputeCompensationPort} (spec §4.3): when the
 * payment fails the ruling stays, the dispute waits in PENDING_COMPENSATION and support retries
 * ({@link #payCompensation}).
 *
 * @author MANFOUO Braun
 */
@Service
public class DisputeCommandService implements IDisputeCommandUseCase, IDisputeSystemUseCase {

    private static final Logger log = LoggerFactory.getLogger(DisputeCommandService.class);

    /** Row LT11: states in which a dispute expires. */
    private static final Set<DisputeStatus> EXPIRABLE = EnumSet.of(DisputeStatus.OPEN,
            DisputeStatus.UNDER_INVESTIGATION, DisputeStatus.AWAITING_EVIDENCE);

    private final IDisputeRepository repository;
    private final IDisputeNotificationPort notificationPort;
    private final IDisputeParcelPort parcels;
    private final IDisputeCompensationPort compensations;
    private final IBlockchainProofPort blockchainProofPort;
    private final IDisputeReferenceGenerator referenceGenerator;

    public DisputeCommandService(
            final IDisputeRepository repository,
            final IDisputeNotificationPort notificationPort,
            final IDisputeParcelPort parcels,
            final IDisputeCompensationPort compensations,
            final IBlockchainProofPort blockchainProofPort,
            final IDisputeReferenceGenerator referenceGenerator) {
        this.repository = Objects.requireNonNull(repository);
        this.notificationPort = Objects.requireNonNull(notificationPort);
        this.parcels = Objects.requireNonNull(parcels);
        this.compensations = Objects.requireNonNull(compensations);
        this.blockchainProofPort = Objects.requireNonNull(blockchainProofPort);
        this.referenceGenerator = Objects.requireNonNull(referenceGenerator);
    }

    // =========================================================================
    // LT1 — opening
    // =========================================================================

    @Override
    @Transactional
    @RequirePermission(resource = "dispute", action = "create")
    public Mono<Dispute> openDispute(final OpenDisputeCommand cmd) {
        return open(cmd);
    }

    @Override
    @Transactional
    public Mono<Dispute> openFromIncident(final OpenDisputeCommand cmd) {
        if (cmd.incidentId() == null) {
            return open(cmd);
        }
        // a redelivered IncidentEscalatedToDispute does not open a second dispute
        return repository.findByIncidentId(cmd.incidentId(), cmd.tenantId())
                .doOnNext(existing -> log.info("Incident {} already escalated into dispute {}", cmd.incidentId(),
                        existing.getId()))
                .switchIfEmpty(Mono.defer(() -> open(cmd)));
    }

    private Mono<Dispute> open(final OpenDisputeCommand cmd) {
        Objects.requireNonNull(cmd, "OpenDisputeCommand must not be null");
        log.info("Opening dispute for package={} mission={} by claimant={} in tenant={}",
                cmd.packageId(), cmd.missionId(), cmd.claimantId(), cmd.tenantId());

        return parcels.resolveParcel(cmd.tenantId(), cmd.packageId(), cmd.missionId())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(parcel -> {
                    final OpenDisputeCommand effective = parcel.map(id -> withPackage(cmd, id)).orElse(cmd);
                    return checkNoDuplicateDispute(effective)
                            .then(referenceGenerator.nextReference())
                            .map(reference -> Dispute.open(effective, reference))
                            .flatMap(dispute -> parcel.isEmpty() ? Mono.just(dispute)
                                    : parcels.openDispute(effective.tenantId(), parcel.get(),
                                                    dispute.getId().getValue(), freezesDelivery(effective.cause()),
                                                    Dispute.actorOf(effective.claimantType(), effective.claimantId()))
                                            .thenReturn(dispute));
                })
                .flatMap(repository::save)
                .flatMap(saved -> afterChange(saved, notificationPort.notifyDisputeOpened(saved)))
                .doOnSuccess(d -> log.info("Dispute opened: id={}, ref={}", d.getId(), d.getReference()))
                .doOnError(e -> log.error("Failed to open dispute: {}", e.getMessage()));
    }

    /** QO-7: a delay does not freeze the delivery; any other cause does. */
    static boolean freezesDelivery(final DisputeCause cause) {
        return cause != DisputeCause.DELIVERY_DELAYED;
    }

    private static OpenDisputeCommand withPackage(final OpenDisputeCommand c, final String parcelId) {
        if (parcelId.equals(c.packageId())) {
            return c;
        }
        return new OpenDisputeCommand(c.tenantId(), c.claimantId(), c.claimantType(), c.respondentId(),
                c.respondentType(), c.cause(), c.category(), c.priority(), c.missionId(), parcelId, c.trackingCode(),
                c.description(), c.respondentOrgId(), c.impliedSubDelivererId(), c.subDelivererInvolved(),
                c.incidentId());
    }

    // =========================================================================
    // LT2–LT6
    // =========================================================================

    @Override
    @Transactional
    @RequirePermission(resource = "dispute", action = "resolve")
    public Mono<Dispute> assignMediator(final AssignMediatorCommand cmd) {
        Objects.requireNonNull(cmd, "AssignMediatorCommand must not be null");
        log.info("Assigning mediator={} to dispute={}", cmd.mediatorId(), cmd.disputeId());

        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> {
                    dispute.assignMediator(cmd.mediatorId());
                    return repository.save(dispute);
                })
                .flatMap(saved -> afterChange(saved, notificationPort.notifyMediatorAssigned(saved)));
    }

    @Override
    @Transactional
    @RequirePermission(resource = "dispute", action = "resolve")
    public Mono<Dispute> startMediation(final StartMediationCommand cmd) {
        Objects.requireNonNull(cmd, "StartMediationCommand must not be null");
        log.info("Starting mediation for dispute={}", cmd.disputeId());

        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> {
                    dispute.startMediation();
                    return repository.save(dispute);
                });
    }

    @Override
    @Transactional
    @RequirePermission(resource = "dispute", action = "resolve")
    public Mono<Dispute> escalateDispute(final EscalateDisputeCommand cmd) {
        Objects.requireNonNull(cmd, "EscalateDisputeCommand must not be null");
        log.info("Escalating dispute={} by={}", cmd.disputeId(), cmd.escalatedBy());

        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> {
                    dispute.escalate(cmd);
                    return repository.save(dispute);
                })
                .flatMap(saved -> afterChange(saved, Mono.empty()));
    }

    // =========================================================================
    // LT7 / LT8 — ruling
    // =========================================================================

    @Override
    @Transactional
    @RequirePermission(resource = "dispute", action = "resolve")
    public Mono<Dispute> ruleDispute(final RuleDisputeCommand cmd) {
        Objects.requireNonNull(cmd, "RuleDisputeCommand must not be null");
        log.info("Ruling dispute={} with resolution={}", cmd.disputeId(), cmd.resolutionType());

        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> {
                    dispute.rule(cmd);
                    if (dispute.getStatus() == DisputeStatus.CLOSED_RESOLVED) {
                        // LT8: the parcel outcome now (P13 / P16 / P17)
                        return applyOutcome(dispute, cmd.resolutionType().parcelOutcome(),
                                "DISPUTE_" + cmd.resolutionType().name(), null, support(cmd.ruledBy()))
                                .then(repository.save(dispute));
                    }
                    return repository.save(dispute);
                })
                .flatMap(saved -> afterChange(saved, notificationPort.notifyRulingIssued(saved)))
                .flatMap(saved -> saved.getStatus() == DisputeStatus.PENDING_COMPENSATION
                        // LT7: the payment is launched; a failure leaves the dispute pending (spec §4.3)
                        ? pay(saved).onErrorResume(DisputeCompensationException.class, e -> {
                            log.warn("Compensation of dispute {} not paid ({}): it stays PENDING_COMPENSATION",
                                    saved.getId(), e.getCode());
                            return Mono.just(saved);
                        })
                        : Mono.just(saved));
    }

    // =========================================================================
    // LT9 — compensation paid
    // =========================================================================

    @Override
    @Transactional
    // guarded by @RequirePermission on DisputeController.payCompensation: no new annotation in the application layer
    // (frozen ArchUnit rule applicationMustNotDependOnAdapterInWeb)
    public Mono<Dispute> payCompensation(final DisputeId disputeId, final String tenantId) {
        return loadDispute(disputeId, tenantId).flatMap(dispute -> {
            if (dispute.getStatus() != DisputeStatus.PENDING_COMPENSATION) {
                return Mono.error(new DisputeStateException("Dispute " + disputeId
                        + " is not waiting for a compensation (status " + dispute.getStatus() + ")"));
            }
            return pay(dispute);
        });
    }

    /** Pays from the Kernel fund then LT9 with the parcel outcome. */
    private Mono<Dispute> pay(final Dispute dispute) {
        final CompensationDetails c = dispute.getCompensation();
        final String beneficiary = c.getBeneficiaryId() != null ? c.getBeneficiaryId() : dispute.getClaimantId();
        return compensations.pay(dispute.getTenantId(), dispute.getId().getValue(), beneficiary, c.getAmount(),
                        c.getCurrency())
                .flatMap(paymentRef -> compensated(dispute, paymentRef));
    }

    @Override
    @Transactional
    @RequirePermission(resource = "dispute", action = "resolve")
    public Mono<Dispute> processCompensation(final ProcessCompensationCommand cmd) {
        return confirmCompensation(cmd);
    }

    @Override
    @Transactional
    public Mono<Dispute> confirmCompensationPaid(final ProcessCompensationCommand cmd) {
        // tnt.billing.compensation.paid is also published when the payment was made synchronously by
        // payCompensation / ruleDispute (lot L5): the dispute is then already COMPENSATED — a redelivered or
        // late record is acknowledged without a change, never retried into the DLQ.
        Objects.requireNonNull(cmd, "ProcessCompensationCommand must not be null");
        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> dispute.getStatus() == DisputeStatus.COMPENSATED
                        ? Mono.just(dispute)
                        : compensated(dispute, cmd.paymentReference()));
    }

    private Mono<Dispute> confirmCompensation(final ProcessCompensationCommand cmd) {
        Objects.requireNonNull(cmd, "ProcessCompensationCommand must not be null");
        log.info("Processing compensation for dispute={} ref={}", cmd.disputeId(), cmd.paymentReference());
        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> compensated(dispute, cmd.paymentReference()));
    }

    private Mono<Dispute> compensated(final Dispute dispute, final String paymentRef) {
        dispute.processCompensation(paymentRef);
        final ResolutionType type = dispute.getResolution().getType();
        return applyOutcome(dispute, type.parcelOutcome(), "DISPUTE_" + type.name(),
                        dispute.getCompensation().getAmount(), TntEventActor.system())
                .then(repository.save(dispute))
                .flatMap(saved -> afterChange(saved, notificationPort.notifyCompensationPaid(saved)));
    }

    // =========================================================================
    // LT10 / LT11 / LT12 — withdrawal, expiry, administrative closure
    // =========================================================================

    @Override
    @Transactional
    @RequirePermission(resource = "dispute", action = "resolve")
    public Mono<Dispute> closeDispute(final CloseDisputeCommand cmd) {
        Objects.requireNonNull(cmd, "CloseDisputeCommand must not be null");
        log.info("Closing dispute={} type={}", cmd.disputeId(), cmd.closureType());

        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> {
                    dispute.close(cmd);
                    return applyOutcome(dispute, "RESTORE_PREVIOUS", "DISPUTE_ADMINISTRATIVE_CLOSURE", null,
                            support(cmd.closedBy())).then(repository.save(dispute));
                })
                .flatMap(saved -> afterChange(saved, notificationPort.notifyDisputeClosed(saved)));
    }

    @Override
    @Transactional
    @RequirePermission(resource = "dispute", action = "create")
    public Mono<Dispute> withdrawDispute(final WithdrawDisputeCommand cmd) {
        Objects.requireNonNull(cmd, "WithdrawDisputeCommand must not be null");
        log.info("Withdrawing dispute={} by claimant={}", cmd.disputeId(), cmd.claimantId());

        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> {
                    dispute.withdraw(cmd.claimantId());
                    return applyOutcome(dispute, "RESTORE_PREVIOUS", "DISPUTE_WITHDRAWN", null,
                            Dispute.actorOf(dispute.getClaimantType(), dispute.getClaimantId()))
                            .then(repository.save(dispute));
                })
                .flatMap(saved -> afterChange(saved, notificationPort.notifyDisputeClosed(saved)));
    }

    @Override
    @Transactional
    public Mono<Dispute> expire(final DisputeId disputeId, final String tenantId) {
        return loadDispute(disputeId, tenantId)
                .filter(dispute -> EXPIRABLE.contains(dispute.getStatus()))
                .flatMap(dispute -> {
                    log.warn("Expiring dispute id={} ref={} status={}", dispute.getId(), dispute.getReference(),
                            dispute.getStatus());
                    dispute.expire();
                    return applyOutcome(dispute, "RESTORE_PREVIOUS", "DISPUTE_EXPIRED", null, TntEventActor.system())
                            .then(repository.save(dispute));
                })
                .flatMap(saved -> afterChange(saved, notificationPort.notifyDisputeClosed(saved)));
    }

    // =========================================================================
    // Comments
    // =========================================================================

    @Override
    @Transactional
    @RequirePermission(resource = "dispute", action = "create")
    public Mono<Dispute> addComment(final AddCommentCommand cmd) {
        Objects.requireNonNull(cmd, "AddCommentCommand must not be null");
        log.debug("Adding comment to dispute={} by={}", cmd.disputeId(), cmd.authorId());

        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> {
                    final DisputeComment comment = DisputeComment.post(
                            cmd.disputeId(), cmd.authorId(), cmd.authorType(),
                            cmd.content(), cmd.isInternal());
                    dispute.addComment(comment);
                    return repository.save(dispute);
                });
    }

    @Override
    @RequirePermission(resource = "dispute", action = "create")
    public Mono<Dispute> openAgainstFreelancerOrg(OpenDisputeAgainstFreelancerOrgCommand cmd) {
        log.info("Opening dispute against FreelancerOrg={} missionId={} tenant={}",
                cmd.freelancerOrgId(), cmd.missionId(), cmd.tenantId());
        return open(cmd.toOpenDisputeCommand());
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private Mono<Dispute> loadDispute(final DisputeId id, final String tenantId) {
        return repository.findByIdAndTenantId(id, tenantId)
                .switchIfEmpty(Mono.error(new DisputeNotFoundException(id)));
    }

    /** Parcel effect of the outcome, when the dispute is on a core parcel (P13–P17). */
    private Mono<Void> applyOutcome(final Dispute dispute, final String outcome, final String trigger,
                                    final java.math.BigDecimal amount, final TntEventActor actor) {
        if (dispute.getPackageId() == null) {
            return Mono.empty();
        }
        return parcels.applyOutcome(dispute.getTenantId(), dispute.getPackageId(), dispute.getId().getValue(),
                outcome, trigger, amount, dispute.getResponsibleParty(), actor);
    }

    /**
     * After the committed change: the notification, best effort — {@code tnt.dispute.events} is already in the outbox
     * (lot L8: the legacy per-type topics are gone, spec §3.9).
     */
    private Mono<Dispute> afterChange(final Dispute saved, final Mono<Void> notification) {
        return notification.onErrorResume(e -> {
                    log.warn("Dispute notification failed for {}: {}", saved.getId(), e.getMessage());
                    return Mono.empty();
                })
                .thenReturn(saved);
    }

    private static TntEventActor support(final String id) {
        UUID uuid;
        try {
            uuid = id == null ? null : UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            uuid = null;
        }
        return TntEventActor.of(TntEventActor.Type.SUPPORT, uuid);
    }

    private Mono<Void> checkNoDuplicateDispute(final OpenDisputeCommand cmd) {
        if (cmd.packageId() == null) {
            return Mono.empty();
        }
        return repository.existsActiveDisputeForPackage(cmd.packageId(), cmd.tenantId())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new DisputeStateException(
                                "An active dispute already exists for package: " + cmd.packageId()));
                    }
                    return Mono.empty();
                });
    }
}
