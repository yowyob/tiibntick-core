package com.yowyob.tiibntick.core.dispute.application.service;

import com.yowyob.tiibntick.core.dispute.application.command.AddEvidenceCommand;
import com.yowyob.tiibntick.core.dispute.application.command.RequestEvidenceCommand;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IEvidenceUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IBlockchainProofPort;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeEventPublisher;
import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeRepository;
import com.yowyob.tiibntick.core.dispute.domain.enums.EvidenceType;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeNotFoundException;
import com.yowyob.tiibntick.core.dispute.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Application service for evidence-related operations within a dispute.
 *
 * <p>Optionally anchors high-value evidence (blockchain proofs, GPS traces,
 * delivery proof records) on the blockchain via tnt-trust.
 *
 * @author MANFOUO Braun
 */
@Service
public class EvidenceApplicationService implements IEvidenceUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvidenceApplicationService.class);

    private final IDisputeRepository repository;
    private final IDisputeEventPublisher eventPublisher;
    private final IBlockchainProofPort blockchainProofPort;

    public EvidenceApplicationService(
            final IDisputeRepository repository,
            final IDisputeEventPublisher eventPublisher,
            final IBlockchainProofPort blockchainProofPort) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.blockchainProofPort = Objects.requireNonNull(blockchainProofPort);
    }

    @Override
    @Transactional
    public Mono<Dispute> submitEvidence(final AddEvidenceCommand cmd) {
        Objects.requireNonNull(cmd, "AddEvidenceCommand must not be null");
        log.info("Submitting evidence type={} for dispute={}", cmd.evidenceType(), cmd.disputeId());

        return repository.findByIdAndTenantId(cmd.disputeId(), cmd.tenantId())
                .switchIfEmpty(Mono.error(new DisputeNotFoundException(cmd.disputeId())))
                .flatMap(dispute -> {
                    final DisputeEvidence evidence = DisputeEvidence.create(
                            cmd.disputeId(), cmd.submittedBy(), cmd.submitterType(),
                            cmd.evidenceType(), cmd.fileKey(), cmd.description(), cmd.evidenceHash());
                    dispute.addEvidence(evidence);
                    return anchorOnBlockchainIfNeeded(evidence, dispute)
                            .thenReturn(dispute);
                })
                .flatMap(repository::save)
                .flatMap(saved -> eventPublisher.publishAll(saved).thenReturn(saved));
    }

    @Override
    @Transactional
    public Mono<Dispute> requestEvidence(final RequestEvidenceCommand cmd) {
        Objects.requireNonNull(cmd, "RequestEvidenceCommand must not be null");
        log.info("Requesting evidence from={} for dispute={}", cmd.requestedFrom(), cmd.disputeId());

        return repository.findByIdAndTenantId(cmd.disputeId(), cmd.tenantId())
                .switchIfEmpty(Mono.error(new DisputeNotFoundException(cmd.disputeId())))
                .flatMap(dispute -> {
                    dispute.requestAdditionalEvidence(cmd.requestedFrom(), cmd.deadline());
                    return repository.save(dispute);
                });
    }

    @Override
    @Transactional
    public Mono<DisputeEvidence> verifyEvidence(
            final DisputeId disputeId,
            final String evidenceId,
            final String mediatorId,
            final String tenantId) {
        Objects.requireNonNull(disputeId, "disputeId must not be null");
        Objects.requireNonNull(evidenceId, "evidenceId must not be null");

        return repository.findByIdAndTenantId(disputeId, tenantId)
                .switchIfEmpty(Mono.error(new DisputeNotFoundException(disputeId)))
                .flatMap(dispute -> {
                    final DisputeEvidence evidence = dispute.getEvidences().stream()
                            .filter(e -> e.getId().getValue().equals(evidenceId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Evidence not found: " + evidenceId));
                    evidence.verify(mediatorId);
                    return repository.save(dispute).thenReturn(evidence);
                });
    }

    @Override
    public Flux<DisputeEvidence> getEvidenceForDispute(final DisputeId disputeId, final String tenantId) {
        return repository.findByIdAndTenantId(disputeId, tenantId)
                .switchIfEmpty(Mono.error(new DisputeNotFoundException(disputeId)))
                .flatMapMany(dispute -> Flux.fromIterable(dispute.getEvidences()));
    }

    private Mono<Void> anchorOnBlockchainIfNeeded(final DisputeEvidence evidence, final Dispute dispute) {
        final boolean shouldAnchor = evidence.getType() == EvidenceType.BLOCKCHAIN_PROOF
                || evidence.getType() == EvidenceType.DELIVERY_PROOF_RECORD
                || evidence.getType() == EvidenceType.GPS_TRACE;

        if (!shouldAnchor || evidence.getFileKey() == null) {
            return Mono.empty();
        }

        return blockchainProofPort
                .anchorEvidence(
                        evidence.getId().getValue(),
                        evidence.getFileKey(),
                        dispute.getId().getValue(),
                        dispute.getTenantId(),
                        evidence.getEvidenceHash())
                .doOnNext(evidence::attachBlockchainRef)
                .then();
    }
}
