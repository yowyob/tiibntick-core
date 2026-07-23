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
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.BillingPolicyRecord;
import com.yowyob.tiibntick.core.trust.application.port.out.BillingPolicyRecordRepository;

import java.time.LocalDateTime;

// ============================================================
// R2DBC Entity
// ============================================================

/**
 * R2DBC Entity — {@code BillingPolicyRecordEntity}.
 *
 * <p>Maps to the {@code tnt_trust.billing_policy_records} table.
 * Anti-Corruption Layer between the {@link BillingPolicyRecord} domain VO
 * and the PostgreSQL persistence layer.
 *
 * <p>Implements {@link Persistable} because {@code recordId} is an
 * application-assigned {@link String} (never {@code null}, never DB-generated).
 * Without this, Spring Data R2DBC's default "is this new?" heuristic treats any
 * entity with a non-null {@code @Id} as already persisted and turns
 * {@code ReactiveCrudRepository.save()} into an {@code UPDATE} that matches zero
 * rows for a brand-new record — i.e. new activations would never actually be
 * inserted. {@link #fromDomain(BillingPolicyRecord)} is the only place that
 * constructs a "new" entity; entities hydrated from a row keep {@code isNew} at
 * its default {@code false}.
 *
 * @author MANFOUO Braun
 */
@Table(schema = "tnt_trust", name = "billing_policy_records")
class BillingPolicyRecordEntity implements Persistable<String> {

    @Id
    @Column("record_id")
    private String recordId;

    /** Not persisted — {@code true} only for entities built via {@link #fromDomain(BillingPolicyRecord)}. */
    @Transient
    private boolean isNew = false;

    @Column("policy_id")
    private String policyId;

    @Column("agency_id")
    private String agencyId;

    @Column("tenant_id")
    private String tenantId;

    @Column("policy_summary_json")
    private String policySummaryJson;

    @Column("activated_at")
    private LocalDateTime activatedAt;

    @Column("blockchain_tx_hash")
    private String blockchainTxHash;

    BillingPolicyRecordEntity() {}

    /**
     * Converts a {@link BillingPolicyRecord} domain VO to this persistence entity.
     */
    static BillingPolicyRecordEntity fromDomain(final BillingPolicyRecord record) {
        final BillingPolicyRecordEntity entity = new BillingPolicyRecordEntity();
        entity.recordId = record.getRecordId();
        entity.policyId = record.getPolicyId();
        entity.agencyId = record.getAgencyId();
        entity.tenantId = record.getTenantId();
        entity.policySummaryJson = record.getPolicySummaryJson();
        entity.activatedAt = record.getActivatedAt();
        entity.blockchainTxHash = record.getBlockchainTxHash();
        entity.isNew = true;
        return entity;
    }

    /**
     * Converts this entity to a {@link BillingPolicyRecord} domain VO.
     */
    BillingPolicyRecord toDomain() {
        final BillingPolicyRecord record = new BillingPolicyRecord(
                recordId, policyId, agencyId, tenantId, policySummaryJson, activatedAt);
        if (blockchainTxHash != null && !blockchainTxHash.isBlank()) {
            record.confirmOnChain(blockchainTxHash);
        }
        return record;
    }

    // Getters & setters for R2DBC
    public String getRecordId() { return recordId; }
    public void setRecordId(final String v) { this.recordId = v; }
    public String getPolicyId() { return policyId; }
    public void setPolicyId(final String v) { this.policyId = v; }
    public String getAgencyId() { return agencyId; }
    public void setAgencyId(final String v) { this.agencyId = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(final String v) { this.tenantId = v; }
    public String getPolicySummaryJson() { return policySummaryJson; }
    public void setPolicySummaryJson(final String v) { this.policySummaryJson = v; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(final LocalDateTime v) { this.activatedAt = v; }
    public String getBlockchainTxHash() { return blockchainTxHash; }
    public void setBlockchainTxHash(final String v) { this.blockchainTxHash = v; }

    // ── Persistable ──────────────────────────────────────────────────────────

    @Override
    public String getId() { return recordId; }

    @Override
    public boolean isNew() { return isNew; }
}

// ============================================================
// Spring Data R2DBC Repository
// ============================================================

/**
 * Spring Data R2DBC repository for billing policy activation records.
 * Not exposed directly — wrapped by {@link BillingPolicyRecordRepositoryAdapter}.
 *
 * @author MANFOUO Braun
 */
@Repository
interface BillingPolicyRecordR2dbcRepository extends ReactiveCrudRepository<BillingPolicyRecordEntity, String> {

    /**
     * Updates the blockchain tx hash on the most recent activation record for a
     * policy — a policy may be re-activated, so we target the latest row.
     */
    @Modifying
    @Query("""
            UPDATE tnt_trust.billing_policy_records
            SET blockchain_tx_hash = :txHash
            WHERE record_id = (
                SELECT record_id FROM tnt_trust.billing_policy_records
                WHERE policy_id = :policyId
                ORDER BY activated_at DESC
                LIMIT 1
            )
            """)
    Mono<Void> updateTxHashForLatestActivation(String policyId, String txHash);
}

// ============================================================
// Persistence Adapter (Anti-Corruption Layer)
// ============================================================

/**
 * Persistence Adapter — {@code BillingPolicyRecordRepositoryAdapter}.
 *
 * <p>Implements {@link BillingPolicyRecordRepository} by delegating to
 * {@link BillingPolicyRecordR2dbcRepository}. Performs bidirectional mapping
 * between {@link BillingPolicyRecord} and {@link BillingPolicyRecordEntity}.
 *
 * @author MANFOUO Braun
 */
@Component
public class BillingPolicyRecordRepositoryAdapter implements BillingPolicyRecordRepository {

    private final BillingPolicyRecordR2dbcRepository r2dbcRepository;

    public BillingPolicyRecordRepositoryAdapter(final BillingPolicyRecordR2dbcRepository r2dbcRepository) {
        this.r2dbcRepository = r2dbcRepository;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<BillingPolicyRecord> save(final BillingPolicyRecord record) {
        return r2dbcRepository.save(BillingPolicyRecordEntity.fromDomain(record))
                .map(BillingPolicyRecordEntity::toDomain);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> updateTxHash(final String policyId, final String txHash) {
        return r2dbcRepository.updateTxHashForLatestActivation(policyId, txHash);
    }
}
