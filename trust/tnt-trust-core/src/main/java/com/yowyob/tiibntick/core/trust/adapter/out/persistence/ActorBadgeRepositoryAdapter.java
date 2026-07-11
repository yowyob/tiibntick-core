package com.yowyob.tiibntick.core.trust.adapter.out.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.ActorBadge;
import com.yowyob.tiibntick.core.trust.application.port.out.ActorBadgeRepository;

import java.time.LocalDateTime;

// ============================================================
// R2DBC Entity
// ============================================================

/**
 * R2DBC Entity — {@code ActorBadgeEntity}.
 *
 * <p>Maps to the {@code tnt_trust.actor_badges} table.
 * Anti-Corruption Layer between the {@link ActorBadge} domain VO
 * and the PostgreSQL persistence layer.
 *
 * @author MANFOUO Braun
 */
@Table(schema = "tnt_trust", name = "actor_badges")
class ActorBadgeEntity {

    @Id
    @Column("badge_id")
    private String badgeId;

    @Column("actor_id")
    private String actorId;

    @Column("tenant_id")
    private String tenantId;

    @Column("badge_type")
    private String badgeType;

    @Column("points")
    private int points;

    @Column("awarded_at")
    private LocalDateTime awardedAt;

    @Column("blockchain_tx_hash")
    private String blockchainTxHash;

    @Column("revoked")
    private boolean revoked;

    ActorBadgeEntity() {}

    /**
     * Converts an {@link ActorBadge} domain VO to this persistence entity.
     */
    static ActorBadgeEntity fromDomain(final ActorBadge badge) {
        final ActorBadgeEntity entity = new ActorBadgeEntity();
        entity.badgeId = badge.getBadgeId();
        entity.actorId = badge.getActorId();
        entity.tenantId = badge.getTenantId();
        entity.badgeType = badge.getBadgeType();
        entity.points = badge.getPoints();
        entity.awardedAt = badge.getAwardedAt();
        entity.blockchainTxHash = badge.getBlockchainTxHash();
        entity.revoked = badge.isRevoked();
        return entity;
    }

    /**
     * Converts this entity to an {@link ActorBadge} domain VO.
     */
    ActorBadge toDomain() {
        return ActorBadge.reconstitute(
                badgeId, actorId, tenantId, badgeType, points,
                awardedAt, blockchainTxHash, revoked,
                null); // revokedAt not stored in this table version
    }

    // Getters & setters for R2DBC
    public String getBadgeId() { return badgeId; }
    public void setBadgeId(final String v) { this.badgeId = v; }
    public String getActorId() { return actorId; }
    public void setActorId(final String v) { this.actorId = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(final String v) { this.tenantId = v; }
    public String getBadgeType() { return badgeType; }
    public void setBadgeType(final String v) { this.badgeType = v; }
    public int getPoints() { return points; }
    public void setPoints(final int v) { this.points = v; }
    public LocalDateTime getAwardedAt() { return awardedAt; }
    public void setAwardedAt(final LocalDateTime v) { this.awardedAt = v; }
    public String getBlockchainTxHash() { return blockchainTxHash; }
    public void setBlockchainTxHash(final String v) { this.blockchainTxHash = v; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(final boolean v) { this.revoked = v; }
}

// ============================================================
// Spring Data R2DBC Repository
// ============================================================

/**
 * Spring Data R2DBC repository for actor badges.
 * Not exposed directly — wrapped by {@link ActorBadgeRepositoryAdapter}.
 *
 * @author MANFOUO Braun
 */
@Repository
interface ActorBadgeR2dbcRepository extends ReactiveCrudRepository<ActorBadgeEntity, String> {

    /**
     * Finds all active (non-revoked) badges for an actor.
     */
    @Query("""
            SELECT * FROM tnt_trust.actor_badges
            WHERE actor_id  = :actorId
              AND tenant_id = :tenantId
              AND revoked   = FALSE
            ORDER BY awarded_at DESC
            """)
    Flux<ActorBadgeEntity> findActiveByActorId(String actorId, String tenantId);

    /**
     * Finds a specific active badge by actor and badge type.
     */
    @Query("""
            SELECT * FROM tnt_trust.actor_badges
            WHERE actor_id   = :actorId
              AND badge_type  = :badgeType
              AND tenant_id   = :tenantId
              AND revoked     = FALSE
            ORDER BY awarded_at DESC
            LIMIT 1
            """)
    Mono<ActorBadgeEntity> findByActorAndType(String actorId, String badgeType, String tenantId);

    /**
     * Updates the blockchain tx hash after on-chain confirmation from Kafka.
     */
    @Modifying
    @Query("""
            UPDATE tnt_trust.actor_badges
            SET blockchain_tx_hash = :txHash
            WHERE badge_id = :badgeId
            """)
    Mono<Void> updateTxHash(String badgeId, String txHash);

    /**
     * Marks a badge as revoked.
     */
    @Modifying
    @Query("""
            UPDATE tnt_trust.actor_badges
            SET revoked = TRUE
            WHERE badge_id = :badgeId
            """)
    Mono<Void> revokeByBadgeId(String badgeId);

    /**
     * Checks whether an active badge of the given type exists for an actor.
     */
    @Query("""
            SELECT EXISTS(
                SELECT 1 FROM tnt_trust.actor_badges
                WHERE actor_id  = :actorId
                  AND badge_type = :badgeType
                  AND tenant_id  = :tenantId
                  AND revoked    = FALSE
            )
            """)
    Mono<Boolean> existsActiveByActorAndType(String actorId, String badgeType, String tenantId);
}

// ============================================================
// Persistence Adapter (Anti-Corruption Layer)
// ============================================================

/**
 * Persistence Adapter — {@code ActorBadgeRepositoryAdapter}.
 *
 * <p>Implements {@link ActorBadgeRepository} by delegating to
 * {@link ActorBadgeR2dbcRepository}. Performs bidirectional mapping
 * between {@link ActorBadge} and {@link ActorBadgeEntity}.
 *
 * @author MANFOUO Braun
 */
@Component
public class ActorBadgeRepositoryAdapter implements ActorBadgeRepository {

    private final ActorBadgeR2dbcRepository r2dbcRepository;

    public ActorBadgeRepositoryAdapter(final ActorBadgeR2dbcRepository r2dbcRepository) {
        this.r2dbcRepository = r2dbcRepository;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ActorBadge> save(final ActorBadge badge) {
        return r2dbcRepository.save(ActorBadgeEntity.fromDomain(badge))
                .map(ActorBadgeEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<ActorBadge> findByActorId(final String actorId, final String tenantId) {
        return r2dbcRepository.findActiveByActorId(actorId, tenantId)
                .map(ActorBadgeEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<ActorBadge> findByActorIdAndBadgeType(
            final String actorId, final String badgeType, final String tenantId) {
        return r2dbcRepository.findByActorAndType(actorId, badgeType, tenantId)
                .map(ActorBadgeEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> updateTxHash(final String badgeId, final String txHash) {
        return r2dbcRepository.updateTxHash(badgeId, txHash);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> revokeByBadgeId(final String badgeId) {
        return r2dbcRepository.revokeByBadgeId(badgeId);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Boolean> existsByActorAndType(
            final String actorId, final String badgeType, final String tenantId) {
        return r2dbcRepository.existsActiveByActorAndType(actorId, badgeType, tenantId)
                .defaultIfEmpty(false);
    }
}
