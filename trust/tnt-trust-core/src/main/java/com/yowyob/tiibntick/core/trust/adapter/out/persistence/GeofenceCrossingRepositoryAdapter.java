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
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.GeofenceCrossingRecord;
import com.yowyob.tiibntick.core.trust.application.port.out.GeofenceCrossingRepository;

import java.time.LocalDateTime;

// ============================================================
// R2DBC Entity
// ============================================================

/**
 * R2DBC Entity — {@code GeofenceCrossingEntity}.
 *
 * <p>Maps to the {@code tnt_trust.geofence_crossings} table.
 * Anti-Corruption Layer between the {@link GeofenceCrossingRecord} domain VO
 * and the PostgreSQL persistence layer.
 *
 * <p>Implements {@link Persistable} because {@code crossingId} is an
 * application-assigned {@link String} (never {@code null}, never DB-generated).
 * Without this, Spring Data R2DBC's default "is this new?" heuristic treats any
 * entity with a non-null {@code @Id} as already persisted and turns
 * {@code ReactiveCrudRepository.save()} into an {@code UPDATE} that matches zero
 * rows for a brand-new crossing — i.e. new crossings would never actually be
 * inserted. {@link #fromDomain(GeofenceCrossingRecord)} is the only place that
 * constructs a "new" entity; entities hydrated from a row keep {@code isNew} at
 * its default {@code false}.
 *
 * @author MANFOUO Braun
 */
@Table(schema = "tnt_trust", name = "geofence_crossings")
class GeofenceCrossingEntity implements Persistable<String> {

    @Id
    @Column("crossing_id")
    private String crossingId;

    /** Not persisted — {@code true} only for entities built via {@link #fromDomain(GeofenceCrossingRecord)}. */
    @Transient
    private boolean isNew = false;

    @Column("actor_id")
    private String actorId;

    @Column("tenant_id")
    private String tenantId;

    @Column("zone_id")
    private String zoneId;

    @Column("zone_name")
    private String zoneName;

    @Column("zone_type")
    private String zoneType;

    @Column("direction")
    private String direction;

    @Column("gps_lat")
    private double gpsLat;

    @Column("gps_lng")
    private double gpsLng;

    @Column("mission_id")
    private String missionId;

    @Column("occurred_at")
    private LocalDateTime occurredAt;

    @Column("blockchain_tx_hash")
    private String blockchainTxHash;

    GeofenceCrossingEntity() {}

    /**
     * Converts a {@link GeofenceCrossingRecord} domain VO to this persistence entity.
     */
    static GeofenceCrossingEntity fromDomain(final GeofenceCrossingRecord crossing) {
        final GeofenceCrossingEntity entity = new GeofenceCrossingEntity();
        entity.crossingId = crossing.getCrossingId();
        entity.actorId = crossing.getActorId();
        entity.tenantId = crossing.getTenantId();
        entity.zoneId = crossing.getZoneId();
        entity.zoneName = crossing.getZoneName();
        entity.zoneType = crossing.getZoneType();
        entity.direction = crossing.getDirection();
        entity.gpsLat = crossing.getGpsLat();
        entity.gpsLng = crossing.getGpsLng();
        entity.missionId = crossing.getMissionId();
        entity.occurredAt = crossing.getOccurredAt();
        entity.blockchainTxHash = crossing.getBlockchainTxHash();
        entity.isNew = true;
        return entity;
    }

    /**
     * Converts this entity to a {@link GeofenceCrossingRecord} domain VO.
     */
    GeofenceCrossingRecord toDomain() {
        return GeofenceCrossingRecord.reconstitute(
                crossingId, actorId, tenantId, zoneId, zoneName, zoneType, direction,
                gpsLat, gpsLng, missionId, occurredAt, blockchainTxHash);
    }

    // Getters & setters for R2DBC
    public String getCrossingId() { return crossingId; }
    public void setCrossingId(final String v) { this.crossingId = v; }
    public String getActorId() { return actorId; }
    public void setActorId(final String v) { this.actorId = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(final String v) { this.tenantId = v; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(final String v) { this.zoneId = v; }
    public String getZoneName() { return zoneName; }
    public void setZoneName(final String v) { this.zoneName = v; }
    public String getZoneType() { return zoneType; }
    public void setZoneType(final String v) { this.zoneType = v; }
    public String getDirection() { return direction; }
    public void setDirection(final String v) { this.direction = v; }
    public double getGpsLat() { return gpsLat; }
    public void setGpsLat(final double v) { this.gpsLat = v; }
    public double getGpsLng() { return gpsLng; }
    public void setGpsLng(final double v) { this.gpsLng = v; }
    public String getMissionId() { return missionId; }
    public void setMissionId(final String v) { this.missionId = v; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(final LocalDateTime v) { this.occurredAt = v; }
    public String getBlockchainTxHash() { return blockchainTxHash; }
    public void setBlockchainTxHash(final String v) { this.blockchainTxHash = v; }

    // ── Persistable ──────────────────────────────────────────────────────────

    @Override
    public String getId() { return crossingId; }

    @Override
    public boolean isNew() { return isNew; }
}

// ============================================================
// Spring Data R2DBC Repository
// ============================================================

/**
 * Spring Data R2DBC repository for geofence crossings.
 * Not exposed directly — wrapped by {@link GeofenceCrossingRepositoryAdapter}.
 *
 * @author MANFOUO Braun
 */
@Repository
interface GeofenceCrossingR2dbcRepository extends ReactiveCrudRepository<GeofenceCrossingEntity, String> {

    /**
     * Finds the geofence crossing history for an actor, most recent first.
     */
    @Query("""
            SELECT * FROM tnt_trust.geofence_crossings
            WHERE actor_id  = :actorId
              AND tenant_id = :tenantId
            ORDER BY occurred_at DESC
            """)
    Flux<GeofenceCrossingEntity> findByActorId(String actorId, String tenantId);

    /**
     * Updates the blockchain tx hash after on-chain confirmation from Kafka.
     */
    @Modifying
    @Query("""
            UPDATE tnt_trust.geofence_crossings
            SET blockchain_tx_hash = :txHash
            WHERE crossing_id = :crossingId
            """)
    Mono<Void> updateTxHash(String crossingId, String txHash);
}

// ============================================================
// Persistence Adapter (Anti-Corruption Layer)
// ============================================================

/**
 * Persistence Adapter — {@code GeofenceCrossingRepositoryAdapter}.
 *
 * <p>Implements {@link GeofenceCrossingRepository} by delegating to
 * {@link GeofenceCrossingR2dbcRepository}. Performs bidirectional mapping
 * between {@link GeofenceCrossingRecord} and {@link GeofenceCrossingEntity}.
 *
 * @author MANFOUO Braun
 */
@Component
public class GeofenceCrossingRepositoryAdapter implements GeofenceCrossingRepository {

    private final GeofenceCrossingR2dbcRepository r2dbcRepository;

    public GeofenceCrossingRepositoryAdapter(final GeofenceCrossingR2dbcRepository r2dbcRepository) {
        this.r2dbcRepository = r2dbcRepository;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<GeofenceCrossingRecord> save(final GeofenceCrossingRecord crossing) {
        return r2dbcRepository.save(GeofenceCrossingEntity.fromDomain(crossing))
                .map(GeofenceCrossingEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<GeofenceCrossingRecord> findByActorId(final String actorId, final String tenantId) {
        return r2dbcRepository.findByActorId(actorId, tenantId)
                .map(GeofenceCrossingEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> updateTxHash(final String crossingId, final String txHash) {
        return r2dbcRepository.updateTxHash(crossingId, txHash);
    }
}
