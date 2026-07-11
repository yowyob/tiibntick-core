package com.yowyob.tiibntick.core.trust.adapter.out.persistence;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Persistence layer for incident blockchain records in {@code tnt-trust}.
 *
 * <p>This file contains three components following the Anti-Corruption Layer pattern:
 * <ol>
 *   <li>{@link IncidentBlockchainRecordEntity} — R2DBC entity mapping {@code tnt_trust.incident_blockchain_records}</li>
 *   <li>{@link TrustIncidentBlockchainR2dbcRepository} — Spring Data reactive repository</li>
 *   <li>{@link IncidentBlockchainRecordPersistenceAdapter} — public adapter implementing the domain port</li>
 * </ol>
 *
 * <p>The {@code tnt_trust.incident_blockchain_records} table stores blocks of the
 * local incident-specific blockchain chains. Each incident may have its own chain
 * (prefixed {@code INC-}) when {@code affectedParcelIds.size() > 1}.
 * The chain is maintained locally in PostgreSQL and referenced by {@code tnt-incident-core}
 * via the {@code IBlockchainAuditPort} implemented in
 * {@link com.yowyob.tiibntick.core.trust.adapter.out.incident.IncidentBlockchainAuditAdapter}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */

// ============================================================
// Spring Data R2DBC Repository
// ============================================================

/**
 * Spring Data reactive repository for incident blockchain records.
 * Not exposed directly — wrapped by {@link IncidentBlockchainRecordPersistenceAdapter}.
 *
 * @author MANFOUO Braun
 */
@Repository
interface TrustIncidentBlockchainR2dbcRepository
        extends ReactiveCrudRepository<IncidentBlockchainRecordEntity, UUID> {

    /**
     * Finds the latest block in a given chain (highest block_index).
     * Used to retrieve the previous hash before appending a new block.
     *
     * @param chainId the chain identifier
     * @return a {@link Mono} emitting the latest block, or empty if the chain has no blocks
     */
    @Query("""
            SELECT * FROM tnt_trust.incident_blockchain_records
            WHERE chain_id = :chainId
            ORDER BY block_index DESC
            LIMIT 1
            """)
    Mono<IncidentBlockchainRecordEntity> findLatestByChainId(String chainId);

    /**
     * Retrieves all blocks of a chain ordered ascending by block_index.
     * Used for sequential integrity verification.
     *
     * @param chainId the chain identifier
     * @return a {@link Flux} of blocks, genesis first
     */
    @Query("""
            SELECT * FROM tnt_trust.incident_blockchain_records
            WHERE chain_id = :chainId
            ORDER BY block_index ASC
            """)
    Flux<IncidentBlockchainRecordEntity> findByChainIdOrderByBlockIndexAsc(String chainId);

    /**
     * Counts the number of blocks in a chain.
     * Used to verify chain completeness.
     *
     * @param chainId the chain identifier
     * @return a {@link Mono} emitting the block count
     */
    @Query("""
            SELECT COUNT(*) FROM tnt_trust.incident_blockchain_records
            WHERE chain_id = :chainId
            """)
    Mono<Long> countByChainId(String chainId);

    /**
     * Checks whether a chain with the given ID has at least one block.
     *
     * @param chainId the chain identifier
     * @return a {@link Mono} emitting {@code true} if the chain exists
     */
    @Query("""
            SELECT EXISTS(
                SELECT 1 FROM tnt_trust.incident_blockchain_records
                WHERE chain_id = :chainId
            )
            """)
    Mono<Boolean> existsByChainId(String chainId);
}

// ============================================================
// Public Adapter (used by IncidentBlockchainAuditAdapter)
// ============================================================

/**
 * Persistence Adapter — {@code IncidentBlockchainRecordPersistenceAdapter}.
 *
 * <p>Provides persistence operations for incident blockchain blocks.
 * Used by {@link com.yowyob.tiibntick.core.trust.adapter.out.incident.IncidentBlockchainAuditAdapter}
 * to store and query blocks of incident-specific chains.
 *
 * <p>Each chain is identified by a {@code chainId} (format: {@code INC-{uuid}}).
 * The {@code GENESIS} constant is used as the {@code previousHash} for block index 0.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
@Component
public class IncidentBlockchainRecordPersistenceAdapter {

    /** Sentinel value used as previousHash for genesis blocks (block_index = 0). */
    public static final String GENESIS_HASH = "GENESIS";

    private final TrustIncidentBlockchainR2dbcRepository r2dbcRepository;

    public IncidentBlockchainRecordPersistenceAdapter(
            final TrustIncidentBlockchainR2dbcRepository r2dbcRepository) {
        this.r2dbcRepository = r2dbcRepository;
    }

    /**
     * Finds the latest block in a chain by its chain identifier.
     * Returns empty if no blocks exist yet for this chain.
     *
     * @param chainId the chain identifier
     * @return a {@link Mono} emitting the latest entity, or empty
     */
    public Mono<IncidentBlockchainRecordEntity> findLatestByChainId(final String chainId) {
        return r2dbcRepository.findLatestByChainId(chainId);
    }

    /**
     * Retrieves all blocks of a chain in ascending order (genesis first).
     * Used for sequential integrity verification.
     *
     * @param chainId the chain identifier
     * @return a {@link Flux} of entities, oldest block first
     */
    public Flux<IncidentBlockchainRecordEntity> findAllByChainIdAsc(final String chainId) {
        return r2dbcRepository.findByChainIdOrderByBlockIndexAsc(chainId);
    }

    /**
     * Saves a new blockchain block to the persistence store.
     *
     * @param chainId      the chain identifier
     * @param blockIndex   sequential index (0 = genesis)
     * @param previousHash hash of the previous block (GENESIS for index 0)
     * @param currentHash  SHA-256 hash of this block
     * @param eventType    event type anchored in this block
     * @param payload      JSON payload of the event
     * @param nonce        timestamp nonce
     * @param incidentId   the incident UUID
     * @return a {@link Mono} emitting the saved entity
     */
    public Mono<IncidentBlockchainRecordEntity> saveBlock(
            final String chainId,
            final long blockIndex,
            final String previousHash,
            final String currentHash,
            final String eventType,
            final String payload,
            final long nonce,
            final UUID incidentId) {
        final IncidentBlockchainRecordEntity entity =
                IncidentBlockchainRecordEntity.create(
                        chainId, blockIndex, previousHash,
                        currentHash, eventType, payload, nonce, incidentId);
        return r2dbcRepository.save(entity);
    }

    /**
     * Counts the total number of blocks in a chain.
     *
     * @param chainId the chain identifier
     * @return a {@link Mono} emitting the count
     */
    public Mono<Long> countByChainId(final String chainId) {
        return r2dbcRepository.countByChainId(chainId);
    }

    /**
     * Checks whether a chain with the given ID has been initialized.
     *
     * @param chainId the chain identifier
     * @return a {@link Mono} emitting {@code true} if the chain exists
     */
    public Mono<Boolean> chainExists(final String chainId) {
        return r2dbcRepository.existsByChainId(chainId).defaultIfEmpty(false);
    }
}
