package com.yowyob.tiibntick.core.trust.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;
import com.yowyob.tiibntick.core.trust.domain.policy.LogisticEventCatalog;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.ParcelCustodyChain;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.ParcelDigitalTwin;
import com.yowyob.tiibntick.core.trust.domain.model.enums.ParcelLifecycleState;
import com.yowyob.tiibntick.core.trust.application.port.in.GetCustodyChainUseCase;
import com.yowyob.tiibntick.core.trust.application.port.out.CustodyTransferCacheRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.DeliveryProofCacheRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.DIDRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.TrustProofQueryPort;

import java.time.LocalDateTime;

/**
 * Application Service — {@code LogisticProofResolverService}.
 *
 * <p>Aggregates and resolves blockchain proof data from multiple sources
 * (local PostgreSQL cache + Trust Event REST API) for display in the
 * TiiBnTick platform UIs (Agency, Go, Link, Point).
 *
 * <p>This service is the {@code LogisticProofResolver} component from the
 * architecture diagram. It is a read-only service — no write operations.
 *
 * <h3>Resolution Strategy</h3>
 * <p>Results are resolved using a two-tier approach:
 * <ol>
 *   <li><strong>Local cache (fast)</strong> — queries the local
 *       {@code tnt_trust_db} PostgreSQL tables. Handles 95% of requests.</li>
 *   <li><strong>On-chain verification (slow)</strong> — queries the Trust
 *       Event REST API to verify a specific tx hash on the Fabric ledger.
 *       Used only for explicit verification requests.</li>
 * </ol>
 *
 * <h3>Consumers</h3>
 * <ul>
 *   <li>{@code tnt-link} — Fil d'Ariane tracking view</li>
 *   <li>{@code tnt-agency} — Mission audit trail for agencies</li>
 *   <li>{@code tnt-point} — Relay hub custody chain</li>
 *   <li>{@code TrustApiController} — REST API endpoint</li>
 * </ul>
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Service
public class LogisticProofResolverService {

    private static final Logger log = LoggerFactory.getLogger(LogisticProofResolverService.class);

    private final DeliveryProofCacheRepository proofCacheRepository;
    private final CustodyTransferCacheRepository custodyCacheRepository;
    private final DIDRepository didRepository;
    private final TrustProofQueryPort trustProofQueryPort;
    private final GetCustodyChainUseCase getCustodyChainUseCase;

    public LogisticProofResolverService(
            final DeliveryProofCacheRepository proofCacheRepository,
            final CustodyTransferCacheRepository custodyCacheRepository,
            final DIDRepository didRepository,
            final TrustProofQueryPort trustProofQueryPort,
            final GetCustodyChainUseCase getCustodyChainUseCase) {
        this.proofCacheRepository = proofCacheRepository;
        this.custodyCacheRepository = custodyCacheRepository;
        this.didRepository = didRepository;
        this.trustProofQueryPort = trustProofQueryPort;
        this.getCustodyChainUseCase = getCustodyChainUseCase;
    }

    // ── Delivery Audit Trail (Fil d'Ariane) ───────────────────────────────────

    /**
     * Resolves the complete "Fil d'Ariane" for a delivery mission.
     * Returns all delivery proofs ordered chronologically.
     *
     * <p>Used by the TiiBnTick Link platform to display the tracking timeline.
     *
     * @param missionId the delivery mission identifier
     * @param tenantId  the tenant identifier
     * @return a {@link Flux} of delivery proofs, oldest first
     */
    public Flux<DeliveryProofRecord> resolveDeliveryAuditTrail(
            final String missionId, final String tenantId) {
        log.debug("Resolving delivery audit trail for missionId={}", missionId);
        return proofCacheRepository.findByMissionId(missionId, tenantId);
    }

    /**
     * Resolves the complete chain of custody for a package by its tracking code.
     * Returns all custody transfers ordered chronologically (Fil d'Ariane).
     *
     * <p>Used by the TiiBnTick Point platform to display the relay hub history.
     *
     * @param trackingCode the package tracking code
     * @param tenantId     the tenant identifier
     * @return a {@link Flux} of custody transfers, oldest first
     */
    public Flux<CustodyTransferRecord> resolvePackageCustodyChain(
            final String trackingCode, final String tenantId) {
        log.debug("Resolving custody chain for trackingCode={}", trackingCode);
        return custodyCacheRepository.findByTrackingCode(trackingCode, tenantId);
    }

    // ── On-Chain Verification ─────────────────────────────────────────────────

    /**
     * Verifies a delivery proof on the Hyperledger Fabric ledger.
     * Delegates to the Trust Event REST API for on-chain confirmation.
     *
     * @param txHash       the Fabric transaction hash of the delivery proof
     * @param expectedHash the expected SHA-256 data hash
     * @return a {@link Mono} emitting {@code true} if the proof is valid on-chain
     */
    public Mono<Boolean> verifyProofOnChain(final String txHash, final String expectedHash) {
        log.info("Verifying proof on-chain — txHash={}", txHash);
        return trustProofQueryPort.verifyProof(txHash, expectedHash);
    }

    // ── Actor Identity ────────────────────────────────────────────────────────

    /**
     * Resolves the DID document for a given actor from the local cache.
     *
     * @param actorId  the actor identifier
     * @param tenantId the tenant identifier
     * @return a {@link Mono} emitting the {@link DIDDocument}, or empty if not found
     */
    public Mono<DIDDocument> resolveActorDID(final String actorId, final String tenantId) {
        return didRepository.findByActorId(actorId, tenantId)
                .doOnNext(did -> log.debug("Resolved DID for actorId={}: {}", actorId, did.getDid()));
    }

    /**
     * Returns the human-readable description of an event type from the catalog.
     * Used by audit UIs to display event labels without hardcoded strings.
     *
     * @param eventTypeName the {@link com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType} name
     * @return the description string, or the event type name if not found in catalog
     */
    public String describeEventType(final String eventTypeName) {
        try {
            final com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType type =
                    com.yowyob.tiibntick.core.trust.domain.model.enums.LogisticTrustEventType.valueOf(eventTypeName);
            return LogisticEventCatalog.getEntry(type).description();
        } catch (final IllegalArgumentException e) {
            return eventTypeName;
        }
    }

    // ── Proof History from Trust Event API ────────────────────────────────────

    /**
     * Retrieves the sequence of Fabric tx hashes for all on-chain proofs
     * associated with a given entity, ordered chronologically.
     * Delegates to the Trust Event REST API.
     *
     * @param entityId   the domain entity identifier
     * @param entityType the domain entity type (e.g., "DELIVERY_PROOF")
     * @return a {@link Flux} of tx hashes, oldest first
     */
    public Flux<String> resolveOnChainHistory(
            final String entityId, final String entityType) {
        return trustProofQueryPort.getAuditHistory(entityId, entityType);
    }

    // ── Digital Twin ──────────────────────────────────────────────────────────

    /**
     * Assembles the Digital Twin of a parcel on demand.
     *
     * <p>Combines the Chain of Custody (from blockchain) with the inferred
     * lifecycle state to produce a composite real-time view of the parcel.
     *
     * @param packageId the domain package UUID
     * @param tenantId  the tenant identifier
     * @return the assembled {@link ParcelDigitalTwin}
     */
    public Mono<ParcelDigitalTwin> resolveDigitalTwin(
            final String packageId, final String tenantId) {
        log.debug("Resolving digital twin for packageId={}", packageId);

        return getCustodyChainUseCase.getByPackageId(packageId, tenantId)
                .map(chain -> {
                    final ParcelLifecycleState state = inferState(chain);
                    return new ParcelDigitalTwin(
                            packageId,
                            chain.trackingCode(),
                            tenantId,
                            state,
                            chain,
                            null,
                            chain.chainIntact(),
                            chain.transferCount(),
                            chain.lastTransferAt(),
                            LocalDateTime.now()
                    );
                });
    }

    private static ParcelLifecycleState inferState(final ParcelCustodyChain chain) {
        final CustodyTransferType type = chain.currentCustodyType();
        if (type == null) return ParcelLifecycleState.CREATED;
        return switch (type) {
            case PICKUP_FROM_SENDER -> ParcelLifecycleState.PICKED_UP;
            case TRANSFER_TO_HUB   -> ParcelLifecycleState.AT_RELAY_HUB;
            case PICKUP_FROM_HUB   -> ParcelLifecycleState.IN_TRANSIT;
            case TRANSFER_TO_RECIPIENT -> ParcelLifecycleState.DELIVERED;
            case RETURN_TO_SENDER  -> ParcelLifecycleState.RETURNED;
        };
    }
}
