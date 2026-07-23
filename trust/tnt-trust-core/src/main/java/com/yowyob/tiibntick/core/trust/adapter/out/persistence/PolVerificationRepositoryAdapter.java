package com.yowyob.tiibntick.core.trust.adapter.out.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.PolVerificationRecord;
import com.yowyob.tiibntick.core.trust.application.port.out.PolVerificationRepository;

import java.time.LocalDateTime;

// ============================================================
// R2DBC Entity
// ============================================================

/**
 * R2DBC Entity — {@code PolVerificationEntity}.
 *
 * <p>Maps to the {@code tnt_trust.pol_verifications} table.
 * Anti-Corruption Layer between the {@link PolVerificationRecord} domain VO
 * and the PostgreSQL persistence layer.
 *
 * <p>Implements {@link Persistable} because {@code eventId} is an
 * application-assigned {@link String} (never {@code null}, never DB-generated).
 * Without this, Spring Data R2DBC's default "is this new?" heuristic treats any
 * entity with a non-null {@code @Id} as already persisted and turns
 * {@code ReactiveCrudRepository.save()} into an {@code UPDATE} that matches zero
 * rows for a brand-new verification — i.e. new verifications would never
 * actually be inserted. {@link #fromDomain(PolVerificationRecord)} is the only
 * place that constructs a "new" entity; entities hydrated from a row keep
 * {@code isNew} at its default {@code false}.
 *
 * @author MANFOUO Braun
 */
@Table(schema = "tnt_trust", name = "pol_verifications")
class PolVerificationEntity implements Persistable<String> {

    @Id
    @Column("event_id")
    private String eventId;

    /** Not persisted — {@code true} only for entities built via {@link #fromDomain(PolVerificationRecord)}. */
    @Transient
    private boolean isNew = false;

    @Column("actor_id")
    private String actorId;

    @Column("tenant_id")
    private String tenantId;

    @Column("gps_lat")
    private double gpsLat;

    @Column("gps_lng")
    private double gpsLng;

    @Column("pol_hash")
    private String polHash;

    @Column("verified_at")
    private LocalDateTime verifiedAt;

    @Column("blockchain_tx_hash")
    private String blockchainTxHash;

    PolVerificationEntity() {}

    /**
     * Converts a {@link PolVerificationRecord} domain VO to this persistence entity.
     */
    static PolVerificationEntity fromDomain(final PolVerificationRecord verification) {
        final PolVerificationEntity entity = new PolVerificationEntity();
        entity.eventId = verification.getEventId();
        entity.actorId = verification.getActorId();
        entity.tenantId = verification.getTenantId();
        entity.gpsLat = verification.getGpsLat();
        entity.gpsLng = verification.getGpsLng();
        entity.polHash = verification.getPolHash();
        entity.verifiedAt = verification.getVerifiedAt();
        entity.blockchainTxHash = verification.getBlockchainTxHash();
        entity.isNew = true;
        return entity;
    }

    /**
     * Converts this entity to a {@link PolVerificationRecord} domain VO.
     */
    PolVerificationRecord toDomain() {
        return PolVerificationRecord.reconstitute(
                eventId, actorId, tenantId, gpsLat, gpsLng, polHash, verifiedAt, blockchainTxHash);
    }

    // Getters & setters for R2DBC
    public String getEventId() { return eventId; }
    public void setEventId(final String v) { this.eventId = v; }
    public String getActorId() { return actorId; }
    public void setActorId(final String v) { this.actorId = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(final String v) { this.tenantId = v; }
    public double getGpsLat() { return gpsLat; }
    public void setGpsLat(final double v) { this.gpsLat = v; }
    public double getGpsLng() { return gpsLng; }
    public void setGpsLng(final double v) { this.gpsLng = v; }
    public String getPolHash() { return polHash; }
    public void setPolHash(final String v) { this.polHash = v; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(final LocalDateTime v) { this.verifiedAt = v; }
    public String getBlockchainTxHash() { return blockchainTxHash; }
    public void setBlockchainTxHash(final String v) { this.blockchainTxHash = v; }

    // ── Persistable ──────────────────────────────────────────────────────────

    @Override
    public String getId() { return eventId; }

    @Override
    public boolean isNew() { return isNew; }
}

// ============================================================
// Spring Data R2DBC Repository
// ============================================================

/**
 * Spring Data R2DBC repository for Proof-of-Location verifications.
 * Not exposed directly — wrapped by {@link PolVerificationRepositoryAdapter}.
 *
 * @author MANFOUO Braun
 */
@Repository
interface PolVerificationR2dbcRepository extends ReactiveCrudRepository<PolVerificationEntity, String> {

    /**
     * Finds the Proof-of-Location verification history for an actor, most recent first.
     */
    @Query("""
            SELECT * FROM tnt_trust.pol_verifications
            WHERE actor_id  = :actorId
              AND tenant_id = :tenantId
            ORDER BY verified_at DESC
            """)
    Flux<PolVerificationEntity> findByActorId(String actorId, String tenantId);

    /**
     * Updates the blockchain tx hash after on-chain confirmation from Kafka.
     */
    @Modifying
    @Query("""
            UPDATE tnt_trust.pol_verifications
            SET blockchain_tx_hash = :txHash
            WHERE event_id = :eventId
            """)
    Mono<Void> updateTxHash(String eventId, String txHash);
}

// ============================================================
// Persistence Adapter (Anti-Corruption Layer)
// ============================================================

/**
 * Persistence Adapter — {@code PolVerificationRepositoryAdapter}.
 *
 * <p>Implements {@link PolVerificationRepository} by delegating to
 * {@link PolVerificationR2dbcRepository}. Performs bidirectional mapping
 * between {@link PolVerificationRecord} and {@link PolVerificationEntity}.
 *
 * @author MANFOUO Braun
 */
@Component
public class PolVerificationRepositoryAdapter implements PolVerificationRepository {

    private final PolVerificationR2dbcRepository r2dbcRepository;

    public PolVerificationRepositoryAdapter(final PolVerificationR2dbcRepository r2dbcRepository) {
        this.r2dbcRepository = r2dbcRepository;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<PolVerificationRecord> save(final PolVerificationRecord verification) {
        return r2dbcRepository.save(PolVerificationEntity.fromDomain(verification))
                .map(PolVerificationEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<PolVerificationRecord> findByActorId(final String actorId, final String tenantId) {
        return r2dbcRepository.findByActorId(actorId, tenantId)
                .map(PolVerificationEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> updateTxHash(final String eventId, final String txHash) {
        return r2dbcRepository.updateTxHash(eventId, txHash);
    }
}
