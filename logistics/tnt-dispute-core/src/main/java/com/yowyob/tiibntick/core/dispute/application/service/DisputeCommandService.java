package com.yowyob.tiibntick.core.dispute.application.service;

import com.yowyob.tiibntick.core.dispute.application.command.*;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeCommandUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.*;
import com.yowyob.tiibntick.core.dispute.domain.enums.DisputeStatus;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeNotFoundException;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeStateException;
import com.yowyob.tiibntick.core.dispute.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Application service implementing all dispute write operations.
 *
 * <p>Orchestrates domain logic by:
 * <ol>
 *   <li>Loading the aggregate from the repository</li>
 *   <li>Delegating to the domain aggregate for state transitions</li>
 *   <li>Persisting the updated aggregate</li>
 *   <li>Publishing domain events to Kafka</li>
 *   <li>Dispatching notifications via tnt-notify-core</li>
 * </ol>
 *
 * <p>All operations are executed reactively and maintain tenant isolation.
 *
 * @author MANFOUO Braun
 */
@Service
public class DisputeCommandService implements IDisputeCommandUseCase {

    private static final Logger log = LoggerFactory.getLogger(DisputeCommandService.class);

    private final IDisputeRepository repository;
    private final IDisputeEventPublisher eventPublisher;
    private final IDisputeNotificationPort notificationPort;
    private final IDeliveryStatusPort deliveryStatusPort;
    private final IBillingCompensationPort billingCompensationPort;
    private final IBlockchainProofPort blockchainProofPort;
    private final IDisputeReferenceGenerator referenceGenerator;

    public DisputeCommandService(
            final IDisputeRepository repository,
            final IDisputeEventPublisher eventPublisher,
            final IDisputeNotificationPort notificationPort,
            final IDeliveryStatusPort deliveryStatusPort,
            final IBillingCompensationPort billingCompensationPort,
            final IBlockchainProofPort blockchainProofPort,
            final IDisputeReferenceGenerator referenceGenerator) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.notificationPort = Objects.requireNonNull(notificationPort);
        this.deliveryStatusPort = Objects.requireNonNull(deliveryStatusPort);
        this.billingCompensationPort = Objects.requireNonNull(billingCompensationPort);
        this.blockchainProofPort = Objects.requireNonNull(blockchainProofPort);
        this.referenceGenerator = Objects.requireNonNull(referenceGenerator);
    }

    @Override
    @Transactional
    public Mono<Dispute> openDispute(final OpenDisputeCommand cmd) {
        Objects.requireNonNull(cmd, "OpenDisputeCommand must not be null");
        log.info("Opening dispute for package={} by claimant={} in tenant={}",
                cmd.packageId(), cmd.claimantId(), cmd.tenantId());

        return checkNoDuplicateDispute(cmd)
                .then(referenceGenerator.nextReference())
                .map(reference -> Dispute.open(cmd, reference))
                .flatMap(repository::save)
                .flatMap(saved -> {
                    final String packageId = saved.getPackageId();
                    final String disputeId = saved.getId().getValue();
                    final String tenantId = saved.getTenantId();

                    return Mono.when(
                            eventPublisher.publishAll(saved),
                            notificationPort.notifyDisputeOpened(saved),
                            packageId != null
                                    ? deliveryStatusPort.markPackageAsDisputed(packageId, disputeId, tenantId)
                                    : Mono.empty()
                    ).thenReturn(saved);
                })
                .doOnSuccess(d -> log.info("Dispute opened: id={}, ref={}", d.getId(), d.getReference()))
                .doOnError(e -> log.error("Failed to open dispute: {}", e.getMessage()));
    }

    @Override
    @Transactional
    public Mono<Dispute> assignMediator(final AssignMediatorCommand cmd) {
        Objects.requireNonNull(cmd, "AssignMediatorCommand must not be null");
        log.info("Assigning mediator={} to dispute={}", cmd.mediatorId(), cmd.disputeId());

        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> {
                    dispute.assignMediator(cmd.mediatorId());
                    return repository.save(dispute);
                })
                .flatMap(saved -> Mono.when(
                        eventPublisher.publishAll(saved),
                        notificationPort.notifyMediatorAssigned(saved)
                ).thenReturn(saved));
    }

    @Override
    @Transactional
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
    public Mono<Dispute> ruleDispute(final RuleDisputeCommand cmd) {
        Objects.requireNonNull(cmd, "RuleDisputeCommand must not be null");
        log.info("Ruling dispute={} with resolution={}", cmd.disputeId(), cmd.resolutionType());

        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> {
                    dispute.rule(cmd);
                    return repository.save(dispute);
                })
                .flatMap(saved -> Mono.when(
                        eventPublisher.publishAll(saved),
                        notificationPort.notifyRulingIssued(saved),
                        // If compensation is required, initiate payment
                        saved.getStatus() == DisputeStatus.PENDING_COMPENSATION
                                ? initiateCompensationIfApproved(saved)
                                : Mono.empty()
                ).thenReturn(saved));
    }

    @Override
    @Transactional
    public Mono<Dispute> escalateDispute(final EscalateDisputeCommand cmd) {
        Objects.requireNonNull(cmd, "EscalateDisputeCommand must not be null");
        log.info("Escalating dispute={} by={}", cmd.disputeId(), cmd.escalatedBy());

        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> {
                    dispute.escalate(cmd);
                    return repository.save(dispute);
                })
                .flatMap(saved -> eventPublisher.publishAll(saved).thenReturn(saved));
    }

    @Override
    @Transactional
    public Mono<Dispute> processCompensation(final ProcessCompensationCommand cmd) {
        Objects.requireNonNull(cmd, "ProcessCompensationCommand must not be null");
        log.info("Processing compensation for dispute={} ref={}", cmd.disputeId(), cmd.paymentReference());

        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> {
                    dispute.processCompensation(cmd.paymentReference());
                    return repository.save(dispute);
                })
                .flatMap(saved -> Mono.when(
                        eventPublisher.publishAll(saved),
                        notificationPort.notifyCompensationPaid(saved),
                        releasePackageAfterClosure(saved)
                ).thenReturn(saved));
    }

    @Override
    @Transactional
    public Mono<Dispute> closeDispute(final CloseDisputeCommand cmd) {
        Objects.requireNonNull(cmd, "CloseDisputeCommand must not be null");
        log.info("Closing dispute={} type={}", cmd.disputeId(), cmd.closureType());

        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> {
                    dispute.close(cmd);
                    return repository.save(dispute);
                })
                .flatMap(saved -> Mono.when(
                        eventPublisher.publishAll(saved),
                        notificationPort.notifyDisputeClosed(saved),
                        releasePackageAfterClosure(saved)
                ).thenReturn(saved));
    }

    @Override
    @Transactional
    public Mono<Dispute> withdrawDispute(final WithdrawDisputeCommand cmd) {
        Objects.requireNonNull(cmd, "WithdrawDisputeCommand must not be null");
        log.info("Withdrawing dispute={} by claimant={}", cmd.disputeId(), cmd.claimantId());

        return loadDispute(cmd.disputeId(), cmd.tenantId())
                .flatMap(dispute -> {
                    dispute.withdraw(cmd.claimantId());
                    return repository.save(dispute);
                })
                .flatMap(saved -> Mono.when(
                        eventPublisher.publishAll(saved),
                        notificationPort.notifyDisputeClosed(saved),
                        releasePackageAfterClosure(saved)
                ).thenReturn(saved));
    }

    @Override
    @Transactional
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

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private Mono<Dispute> loadDispute(final DisputeId id, final String tenantId) {
        return repository.findByIdAndTenantId(id, tenantId)
                .switchIfEmpty(Mono.error(new DisputeNotFoundException(id)));
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

    private Mono<Void> initiateCompensationIfApproved(final Dispute dispute) {
        if (dispute.getCompensation() == null) {
            return Mono.empty();
        }
        return billingCompensationPort
                .initiateCompensationPayment(
                        dispute.getId().getValue(),
                        dispute.getTenantId(),
                        dispute.getCompensation())
                .doOnNext(ref -> log.info("Compensation payment initiated for dispute={} ref={}", dispute.getId(), ref))
                .then();
    }

    private Mono<Void> releasePackageAfterClosure(final Dispute dispute) {
        if (dispute.getPackageId() == null) {
            return Mono.empty();
        }
        final String outcome = dispute.getResolution() != null
                ? dispute.getResolution().getType().name()
                : "CLOSED";
        return deliveryStatusPort.releasePackageFromDispute(
                dispute.getPackageId(), dispute.getId().getValue(), outcome, dispute.getTenantId());
    }
    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link #openDispute(OpenDisputeCommand)} after converting
     * the FreelancerOrg-specific command to a standard {@link OpenDisputeCommand}.
     */
    @Override
    public Mono<Dispute> openAgainstFreelancerOrg(OpenDisputeAgainstFreelancerOrgCommand cmd) {
        log.info("Opening dispute against FreelancerOrg={} missionId={} tenant={}",
                cmd.freelancerOrgId(), cmd.missionId(), cmd.tenantId());
        return openDispute(cmd.toOpenDisputeCommand());
    }

}