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
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DaoRuleRecord;
import com.yowyob.tiibntick.core.trust.application.port.out.DaoRuleRepository;

import java.time.LocalDateTime;

// ============================================================
// R2DBC Entity
// ============================================================

/**
 * R2DBC Entity — {@code DaoRuleEntity}.
 *
 * <p>Maps to the {@code tnt_trust.dao_rule_records} table.
 * Anti-Corruption Layer between the {@link DaoRuleRecord} domain VO
 * and the PostgreSQL persistence layer.
 *
 * <p>Implements {@link Persistable} because {@code ruleId} is an
 * application-assigned {@link String} (never {@code null}, never DB-generated).
 * Without this, Spring Data R2DBC's default "is this new?" heuristic treats any
 * entity with a non-null {@code @Id} as already persisted and turns
 * {@code ReactiveCrudRepository.save()} into an {@code UPDATE} that matches zero
 * rows for a brand-new rule — i.e. new activations would never actually be
 * inserted. {@link #fromDomain(DaoRuleRecord)} is the only place that
 * constructs a "new" entity; entities hydrated from a row keep {@code isNew} at
 * its default {@code false}.
 *
 * @author MANFOUO Braun
 */
@Table(schema = "tnt_trust", name = "dao_rule_records")
class DaoRuleEntity implements Persistable<String> {

    @Id
    @Column("rule_id")
    private String ruleId;

    /** Not persisted — {@code true} only for entities built via {@link #fromDomain(DaoRuleRecord)}. */
    @Transient
    private boolean isNew = false;

    @Column("zone_id")
    private String zoneId;

    @Column("tenant_id")
    private String tenantId;

    @Column("rule_json")
    private String ruleJson;

    @Column("activated_at")
    private LocalDateTime activatedAt;

    @Column("blockchain_tx_hash")
    private String blockchainTxHash;

    DaoRuleEntity() {}

    /**
     * Converts a {@link DaoRuleRecord} domain VO to this persistence entity.
     */
    static DaoRuleEntity fromDomain(final DaoRuleRecord rule) {
        final DaoRuleEntity entity = new DaoRuleEntity();
        entity.ruleId = rule.getRuleId();
        entity.zoneId = rule.getZoneId();
        entity.tenantId = rule.getTenantId();
        entity.ruleJson = rule.getRuleJson();
        entity.activatedAt = rule.getActivatedAt();
        entity.blockchainTxHash = rule.getBlockchainTxHash();
        entity.isNew = true;
        return entity;
    }

    /**
     * Converts this entity to a {@link DaoRuleRecord} domain VO.
     */
    DaoRuleRecord toDomain() {
        return DaoRuleRecord.reconstitute(
                ruleId, zoneId, tenantId, ruleJson, activatedAt, blockchainTxHash);
    }

    // Getters & setters for R2DBC
    public String getRuleId() { return ruleId; }
    public void setRuleId(final String v) { this.ruleId = v; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(final String v) { this.zoneId = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(final String v) { this.tenantId = v; }
    public String getRuleJson() { return ruleJson; }
    public void setRuleJson(final String v) { this.ruleJson = v; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(final LocalDateTime v) { this.activatedAt = v; }
    public String getBlockchainTxHash() { return blockchainTxHash; }
    public void setBlockchainTxHash(final String v) { this.blockchainTxHash = v; }

    // ── Persistable ──────────────────────────────────────────────────────────

    @Override
    public String getId() { return ruleId; }

    @Override
    public boolean isNew() { return isNew; }
}

// ============================================================
// Spring Data R2DBC Repository
// ============================================================

/**
 * Spring Data R2DBC repository for DAO rule records.
 * Not exposed directly — wrapped by {@link DaoRuleRepositoryAdapter}.
 *
 * @author MANFOUO Braun
 */
@Repository
interface DaoRuleR2dbcRepository extends ReactiveCrudRepository<DaoRuleEntity, String> {

    /**
     * Finds the DAO rule activation history for a zone, most recent first.
     */
    @Query("""
            SELECT * FROM tnt_trust.dao_rule_records
            WHERE zone_id   = :zoneId
              AND tenant_id = :tenantId
            ORDER BY activated_at DESC
            """)
    Flux<DaoRuleEntity> findByZoneId(String zoneId, String tenantId);

    /**
     * Updates the blockchain tx hash after on-chain confirmation from Kafka.
     */
    @Modifying
    @Query("""
            UPDATE tnt_trust.dao_rule_records
            SET blockchain_tx_hash = :txHash
            WHERE rule_id = :ruleId
            """)
    Mono<Void> updateTxHash(String ruleId, String txHash);
}

// ============================================================
// Persistence Adapter (Anti-Corruption Layer)
// ============================================================

/**
 * Persistence Adapter — {@code DaoRuleRepositoryAdapter}.
 *
 * <p>Implements {@link DaoRuleRepository} by delegating to
 * {@link DaoRuleR2dbcRepository}. Performs bidirectional mapping
 * between {@link DaoRuleRecord} and {@link DaoRuleEntity}.
 *
 * @author MANFOUO Braun
 */
@Component
public class DaoRuleRepositoryAdapter implements DaoRuleRepository {

    private final DaoRuleR2dbcRepository r2dbcRepository;

    public DaoRuleRepositoryAdapter(final DaoRuleR2dbcRepository r2dbcRepository) {
        this.r2dbcRepository = r2dbcRepository;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<DaoRuleRecord> save(final DaoRuleRecord rule) {
        return r2dbcRepository.save(DaoRuleEntity.fromDomain(rule))
                .map(DaoRuleEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Flux<DaoRuleRecord> findByZoneId(final String zoneId, final String tenantId) {
        return r2dbcRepository.findByZoneId(zoneId, tenantId)
                .map(DaoRuleEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> updateTxHash(final String ruleId, final String txHash) {
        return r2dbcRepository.updateTxHash(ruleId, txHash);
    }
}
