package com.yowyob.tiibntick.core.trust.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.LogisticTrustEvent;
import com.yowyob.tiibntick.core.trust.application.port.in.GetActorDIDUseCase;
import com.yowyob.tiibntick.core.trust.application.port.in.IssueDIDUseCase;
import com.yowyob.tiibntick.core.trust.application.port.out.DIDRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustProofQueryPort;

/**
 * Application Service — {@code DIDManagerService}.
 *
 * <p>Manages the lifecycle of Decentralized Identifiers (DIDs) for
 * TiiBnTick deliverer actors. Implements:
 * <ul>
 *   <li>{@link IssueDIDUseCase} — issues and revokes DIDs, anchoring on Fabric</li>
 *   <li>{@link GetActorDIDUseCase} — retrieves and verifies DID documents</li>
 * </ul>
 *
 * <h3>DID Issuance Flow</h3>
 * <pre>
 *   actorId + tenantId + publicKeyPem
 *     → DIDDocument.issue(...)                  (domain factory)
 *     → DIDRepository.save(...)                 (local PostgreSQL cache)
 *     → LogisticTrustEvent.forDIDIssuance(...)  (build event)
 *     → LogisticEventPublisherService.publish(event) → Kafka
 *     → return DIDDocument
 * </pre>
 *
 * <h3>Badge Verification</h3>
 * <p>Badge verification uses the local cache to avoid repeated REST calls.
 * The badge data is stored in the {@code tnt_trust.actor_badges} table and
 * updated when {@code yow.trust.events.committed} notifications arrive.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Service
public class DIDManagerService implements IssueDIDUseCase, GetActorDIDUseCase {

    private static final Logger log = LoggerFactory.getLogger(DIDManagerService.class);

    private final DIDRepository didRepository;
    private final LogisticEventPublisherService publisherService;
    private final TrustProofQueryPort trustProofQueryPort;
    private final MeterRegistry meterRegistry;

    public DIDManagerService(
            final DIDRepository didRepository,
            final LogisticEventPublisherService publisherService,
            final TrustProofQueryPort trustProofQueryPort,
            final MeterRegistry meterRegistry) {
        this.didRepository = didRepository;
        this.publisherService = publisherService;
        this.trustProofQueryPort = trustProofQueryPort;
        this.meterRegistry = meterRegistry;
    }

    // ── IssueDIDUseCase ───────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Issues a new DID, saves it locally, and publishes a
     * {@link LogisticTrustEvent} with type {@code DELIVERER_DID_ISSUED} to Kafka.
     */
    @Override
    @Transactional
    public Mono<DIDDocument> issue(
            final String actorId,
            final String tenantId,
            final String publicKeyPem) {
        log.info("Issuing DID for actorId={}, tenant={}", actorId, tenantId);

        // Check if a non-revoked DID already exists
        return didRepository.findByActorId(actorId, tenantId)
                .filter(existing -> !existing.isRevoked() && !existing.isExpired())
                .switchIfEmpty(createAndPublishDID(actorId, tenantId, publicKeyPem))
                .doOnSuccess(did ->
                        log.info("DID issued/retrieved — did={}", did.getDid()));
    }

    private Mono<DIDDocument> createAndPublishDID(
            final String actorId,
            final String tenantId,
            final String publicKeyPem) {
        final DIDDocument document = DIDDocument.issue(
                actorId, tenantId, publicKeyPem,
                "https://api.tiibntick.com/actors/" + actorId + "/identity");

        final LogisticTrustEvent event = LogisticTrustEvent.forDIDIssuance(actorId, document);

        return didRepository.save(document)
                .flatMap(saved -> publisherService.publish(event).thenReturn(saved))
                .doOnSuccess(d -> {
                    meterRegistry.counter("tnt.trust.did.issued",
                            "tenant", tenantId).increment();
                });
    }

    /**
     * Issues a DID for a FreelancerOrganization ().
     * DID format: {@code did:tiibntick:{tenantId}:org:{orgId}}.
     * Anchors FREELANCER_ORG_DID_ISSUED on Fabric.
     *
     * @param orgId       the FreelancerOrg UUID
     * @param tenantId    the tenant identifier
     * @param tradeName   the FreelancerOrg's commercial trade name
     * @param publicKeyPem the org's public key in PEM format
     * @return the issued DIDDocument
     */
    @Override
    @Transactional
    public Mono<DIDDocument> issueForFreelancerOrg(
            final String orgId, final String tenantId,
            final String tradeName, final String publicKeyPem) {
        log.info("Issuing DID for FreelancerOrg={}, tenant={}", orgId, tenantId);

        final DIDDocument document = DIDDocument.issueForFreelancerOrg(orgId, tenantId, tradeName, publicKeyPem);

        final LogisticTrustEvent event =
                LogisticTrustEvent.forFreelancerOrgDIDIssuance(orgId, tenantId, tradeName, document);

        return didRepository.save(document)
                .flatMap(saved -> publisherService.publish(event).thenReturn(saved))
                .doOnSuccess(d -> {
                    meterRegistry.counter("tnt.trust.did.issued.freelancer_org",
                            "tenant", tenantId).increment();
                    log.info("FreelancerOrg DID issued and anchored: did={}", document.getDid());
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Revokes the DID in the local cache and publishes a
     * {@link LogisticTrustEvent} with type {@code DELIVERER_DID_REVOKED}.
     */
    @Override
    @Transactional
    public Mono<Void> revoke(final String did, final String tenantId) {
        log.info("Revoking DID={}", did);

        return didRepository.findByDID(did)
                .flatMap(document -> {
                    document.revoke();
                    return didRepository.save(document)
                            .flatMap(saved -> {
                                final LogisticTrustEvent event = LogisticTrustEvent.forDIDRevocation(
                                        did, document.getActorId(), tenantId);
                                return publisherService.publish(event);
                            });
                })
                .doOnSuccess(v -> {
                    meterRegistry.counter("tnt.trust.did.revoked",
                            "tenant", tenantId).increment();
                    log.info("DID revoked and event published — did={}", did);
                });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Checks the local cache first. If found and the document is
     * verifiable locally (not expired, not revoked), returns immediately.
     * Otherwise, queries the Trust Event REST API to confirm on-chain.
     */
    @Override
    public Mono<Boolean> verify(final String did) {
        return didRepository.findByDID(did)
                .map(DIDDocument::isVerifiable)
                .defaultIfEmpty(false);
    }

    // ── GetActorDIDUseCase ────────────────────────────────────────────────────

    /** {@inheritDoc} */
    @Override
    public Mono<DIDDocument> getByActorId(final String actorId, final String tenantId) {
        return didRepository.findByActorId(actorId, tenantId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Badge verification uses the Trust Proof Query Port to check
     * on-chain evidence of the badge award. Returns {@code false} if the
     * badge has no confirmed on-chain record.
     */
    @Override
    public Mono<Boolean> verifyBadge(
            final String actorId, final String badgeType, final String tenantId) {
        log.debug("Verifying badge actorId={}, badgeType={}", actorId, badgeType);
        // Query the trust-event API for a committed BADGE_AWARDED event for this actor+badgeType
        final String entityId = actorId + "_" + badgeType;
        return trustProofQueryPort.findTxHashByEntityId(entityId, "BADGE", tenantId)
                .map(txHash -> txHash != null && !txHash.isBlank())
                .defaultIfEmpty(false);
    }
}
