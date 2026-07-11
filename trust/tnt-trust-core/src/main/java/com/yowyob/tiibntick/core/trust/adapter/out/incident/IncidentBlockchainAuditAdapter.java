package com.yowyob.tiibntick.core.trust.adapter.out.incident;

import com.yowyob.tiibntick.core.incident.port.outbound.IBlockchainAuditPort;
import com.yowyob.tiibntick.core.trust.adapter.out.persistence.IncidentBlockchainRecordPersistenceAdapter;
import com.yowyob.tiibntick.core.trust.application.port.out.DeliveryProofCacheRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.CustodyTransferCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Incident Adapter — {@code IncidentBlockchainAuditAdapter}.
 *
 * <p>Implements {@code tnt-incident-core}'s {@link IBlockchainAuditPort} outbound port
 * directly. {@code tnt-trust-core} depends on {@code tnt-incident-core} (one-directional —
 * incident never depends back on trust, no Maven cycle) purely to see this port; no
 * {@code tnt-bootstrap} wiring is needed. Plain classpath component scan resolves it: this
 * {@code @Component} bean satisfies incident-core's {@code @ConditionalOnMissingBean(IBlockchainAuditPort.class)}
 * no-op fallback in {@code IncidentCoreConfig}, so the no-op is only used when trust is absent
 * from the classpath (same mechanism as the {@code MissionStatusPortAdapter} precedent).
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Write a new block to an incident chain: SHA-256 hash computation + persistence</li>
 *   <li>Verify chain integrity by traversing all blocks in ascending order</li>
 *   <li>Retrieve the tail hash of a parcel chain (from local delivery proof / custody caches)</li>
 * </ul>
 *
 * <h3>Hash Computation</h3>
 * <p>Each block hash is computed as:
 * <pre>
 *   SHA-256( blockIndex | previousHash | eventType | payload | timestamp | nonce )
 * </pre>
 * using Java's built-in {@link MessageDigest} (no external dependency).
 *
 * <h3>Parcel Chain Tail Hash</h3>
 * <p>The parcel chain tail hash is retrieved from the most recent confirmed
 * delivery proof or custody transfer in the local PostgreSQL cache.
 * If no anchored record exists for the parcel, {@code "GENESIS"} is returned
 * (safe default for incident chain initialization).
 *
 * @author MANFOUO Braun
 * @version 1.1
 * @see IBlockchainAuditPort
 */
@Component
public class IncidentBlockchainAuditAdapter implements IBlockchainAuditPort {

    private static final Logger log = LoggerFactory.getLogger(IncidentBlockchainAuditAdapter.class);

    /** SHA-256 algorithm name for Java's MessageDigest. */
    private static final String SHA_256 = "SHA-256";

    /** Genesis sentinel — used as previousHash for the first block in any chain. */
    private static final String GENESIS = "GENESIS";

    private final IncidentBlockchainRecordPersistenceAdapter persistenceAdapter;
    private final DeliveryProofCacheRepository deliveryProofCacheRepository;
    private final CustodyTransferCacheRepository custodyTransferCacheRepository;

    public IncidentBlockchainAuditAdapter(
            final IncidentBlockchainRecordPersistenceAdapter persistenceAdapter,
            final DeliveryProofCacheRepository deliveryProofCacheRepository,
            final CustodyTransferCacheRepository custodyTransferCacheRepository) {
        this.persistenceAdapter = persistenceAdapter;
        this.deliveryProofCacheRepository = deliveryProofCacheRepository;
        this.custodyTransferCacheRepository = custodyTransferCacheRepository;
    }

    // ── IBlockchainAuditPort contract methods ─────────────────────────────────

    /**
     * Writes a new event block to the specified incident chain.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Retrieve the latest block of the chain (or assume genesis if chain is new)</li>
     *   <li>Compute the SHA-256 hash of the new block</li>
     *   <li>Persist the block to {@code tnt_trust.incident_blockchain_records}</li>
     *   <li>Return the computed hash as the block's identifier</li>
     * </ol>
     *
     * @param incidentId UUID of the incident owning the chain
     * @param chainId    chain identifier (format: INC-{uuid})
     * @param eventType  the event type string (matches {@code LogisticTrustEventType} name)
     * @param payload    JSON payload describing the event
     * @return a {@link Mono} emitting the SHA-256 hash of the newly appended block
     */
    @Override
    public Mono<String> writeIncidentEvent(
            final UUID incidentId,
            final String chainId,
            final String eventType,
            final String payload) {

        log.info("Writing incident block — chainId={}, eventType={}, incidentId={}",
                chainId, eventType, incidentId);

        return persistenceAdapter.findLatestByChainId(chainId)
                .map(prev -> new BlockContext(
                        prev.getBlockIndex() + 1,
                        prev.getCurrentHash()))
                .defaultIfEmpty(new BlockContext(0L, GENESIS))
                .flatMap(ctx -> {
                    final long nonce = Instant.now().toEpochMilli();
                    final String timestamp = Instant.now().toString();
                    final String hash = computeHash(
                            ctx.blockIndex(), ctx.previousHash(),
                            eventType, payload, timestamp, nonce);

                    log.debug("Block computed — chain={}, index={}, hash={}",
                            chainId, ctx.blockIndex(), hash);

                    return persistenceAdapter.saveBlock(
                                    chainId, ctx.blockIndex(), ctx.previousHash(),
                                    hash, eventType, payload, nonce, incidentId)
                            .map(saved -> saved.getCurrentHash());
                })
                .doOnSuccess(hash -> log.info(
                        "Block appended — chainId={}, hash={}", chainId, hash))
                .doOnError(e -> log.error(
                        "Failed to write incident block — chainId={}: {}", chainId, e.getMessage()));
    }

    /**
     * Verifies the sequential integrity of an incident chain.
     *
     * <p>For each block (starting at index 1), verifies that:
     * {@code block[i].previousHash == block[i-1].currentHash}
     *
     * @param chainId the chain identifier to verify
     * @return a {@link Mono} emitting {@code true} if the chain is intact,
     *         {@code false} if any link is broken, or empty if the chain has no blocks
     */
    @Override
    public Mono<Boolean> verifyChain(final String chainId) {
        log.info("Verifying chain integrity — chainId={}", chainId);

        return persistenceAdapter.findAllByChainIdAsc(chainId)
                .collectList()
                .map(blocks -> {
                    if (blocks.isEmpty()) {
                        log.warn("Chain not found or empty — chainId={}", chainId);
                        return true; // empty chain is trivially valid
                    }
                    for (int i = 1; i < blocks.size(); i++) {
                        final String expectedPrev = blocks.get(i - 1).getCurrentHash();
                        final String actualPrev = blocks.get(i).getPreviousHash();
                        if (!expectedPrev.equals(actualPrev)) {
                            log.error("Chain integrity violation at block={} — chainId={}: "
                                            + "expected prev={}, actual prev={}",
                                    i, chainId, expectedPrev, actualPrev);
                            return false;
                        }
                    }
                    log.info("Chain integrity verified — chainId={}, blocks={}",
                            chainId, blocks.size());
                    return true;
                });
    }

    /**
     * Retrieves the tail hash (last anchored hash) of a parcel's delivery chain.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Query the local {@code delivery_proofs} cache for the parcel's
     *       most recent {@code blockchain_tx_hash}</li>
     *   <li>Fall back to the most recent {@code custody_transfers} cache</li>
     *   <li>Return {@code "GENESIS"} if no anchored record exists</li>
     * </ol>
     *
     * @param parcelId the parcel UUID whose chain tail hash is needed
     * @return a {@link Mono} emitting the tail hash or {@code "GENESIS"} if not found
     */
    @Override
    public Mono<String> getParcelChainTailHash(final UUID parcelId) {
        log.debug("Retrieving parcel chain tail hash — parcelId={}", parcelId);

        final String parcelIdStr = parcelId.toString();

        // Fast path: latest confirmed delivery proof for this parcel
        return deliveryProofCacheRepository
                .findLatestConfirmedHashByParcelId(parcelIdStr)
                .switchIfEmpty(
                        // Fallback: latest confirmed custody transfer for this parcel
                        custodyTransferCacheRepository
                                .findLatestConfirmedHashByParcelId(parcelIdStr)
                                .defaultIfEmpty(GENESIS))
                .doOnNext(hash -> log.debug(
                        "Parcel chain tail hash resolved — parcelId={}, hash={}",
                        parcelId, hash));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Computes the SHA-256 hash of a block.
     *
     * <p>Input to SHA-256:
     * {@code blockIndex + "|" + previousHash + "|" + eventType + "|" + payload + "|" + timestamp + "|" + nonce}
     *
     * @param blockIndex   the block's sequential index
     * @param previousHash the hash of the previous block
     * @param eventType    the event type string
     * @param payload      the JSON payload
     * @param timestamp    the ISO-8601 timestamp
     * @param nonce        the timestamp nonce (epoch milliseconds)
     * @return the lowercase hex-encoded SHA-256 hash
     * @throws IllegalStateException if SHA-256 is unavailable (JVM misconfiguration)
     */
    private String computeHash(
            final long blockIndex,
            final String previousHash,
            final String eventType,
            final String payload,
            final String timestamp,
            final long nonce) {
        final String input = blockIndex + "|"
                + (previousHash != null ? previousHash : GENESIS) + "|"
                + (eventType != null ? eventType : "") + "|"
                + (payload != null ? payload : "{}") + "|"
                + timestamp + "|"
                + nonce;
        try {
            final MessageDigest digest = MessageDigest.getInstance(SHA_256);
            final byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (final NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in all Java SE implementations — should never happen
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Internal record to carry block context between reactive steps.
     *
     * @param blockIndex   the index for the next block to be written
     * @param previousHash the hash of the previous block (GENESIS if chain is new)
     */
    private record BlockContext(long blockIndex, String previousHash) {}
}
