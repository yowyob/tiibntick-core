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
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;
import com.yowyob.tiibntick.core.trust.application.port.out.DIDRepository;

import java.time.LocalDateTime;

// ============================================================
// R2DBC Entity
// ============================================================

/**
 * R2DBC Entity — {@code DIDDocumentEntity}.
 * Maps to the {@code tnt_trust.did_documents} table.
 *
 * @author MANFOUO Braun
 */
@Table(schema = "tnt_trust", name = "did_documents")
class DIDDocumentEntity {

    @Id @Column("did") private String did;
    @Column("actor_id") private String actorId;
    @Column("tenant_id") private String tenantId;
    @Column("public_key_pem") private String publicKeyPem;
    @Column("service_endpoint") private String serviceEndpoint;
    @Column("issued_at") private LocalDateTime issuedAt;
    @Column("expires_at") private LocalDateTime expiresAt;
    @Column("blockchain_tx_hash") private String blockchainTxHash;
    @Column("revoked") private boolean revoked;
    @Column("revoked_at") private LocalDateTime revokedAt;
    @Column("subject_type") private String subjectType;
    @Column("org_id") private String orgId;

    DIDDocumentEntity() {}

    static DIDDocumentEntity fromDomain(final DIDDocument doc) {
        final DIDDocumentEntity e = new DIDDocumentEntity();
        e.did = doc.getDid();
        e.actorId = doc.getActorId();
        e.tenantId = doc.getTenantId();
        e.publicKeyPem = doc.getPublicKeyPem();
        e.serviceEndpoint = doc.getServiceEndpoint();
        e.issuedAt = doc.getIssuedAt();
        e.expiresAt = doc.getExpiresAt();
        e.blockchainTxHash = doc.getBlockchainTxHash();
        e.revoked = doc.isRevoked();
        e.revokedAt = doc.getRevokedAt();
        //  fields
        e.subjectType = doc.getSubjectType();
        e.orgId = doc.getOrgId();
        return e;
    }

    DIDDocument toDomain() {
        return DIDDocument.reconstituteFull(did, actorId, tenantId, publicKeyPem, serviceEndpoint,
                issuedAt, expiresAt, blockchainTxHash, revoked, revokedAt, subjectType, orgId);
    }

    // Getters/setters for R2DBC
    public String getDid() { return did; }
    public void setDid(String v) { this.did = v; }
    public String getActorId() { return actorId; }
    public void setActorId(String v) { this.actorId = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { this.tenantId = v; }
    public String getPublicKeyPem() { return publicKeyPem; }
    public void setPublicKeyPem(String v) { this.publicKeyPem = v; }
    public String getServiceEndpoint() { return serviceEndpoint; }
    public void setServiceEndpoint(String v) { this.serviceEndpoint = v; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime v) { this.issuedAt = v; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime v) { this.expiresAt = v; }
    public String getBlockchainTxHash() { return blockchainTxHash; }
    public void setBlockchainTxHash(String v) { this.blockchainTxHash = v; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean v) { this.revoked = v; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime v) { this.revokedAt = v; }
}

// ============================================================
// Spring Data R2DBC Repository
// ============================================================

/**
 * Spring Data R2DBC repository for DID documents.
 *
 * @author MANFOUO Braun
 */
@Repository
interface DIDDocumentR2dbcRepository extends ReactiveCrudRepository<DIDDocumentEntity, String> {

    @Query("SELECT * FROM tnt_trust.did_documents WHERE actor_id=:actorId AND tenant_id=:tenantId AND revoked=false ORDER BY issued_at DESC LIMIT 1")
    Mono<DIDDocumentEntity> findActiveByActorId(String actorId, String tenantId);

    @Query("SELECT * FROM tnt_trust.did_documents WHERE tenant_id=:tenantId AND revoked=false AND expires_at > NOW()")
    Flux<DIDDocumentEntity> findActiveByTenantId(String tenantId);

    @Modifying
    @Query("UPDATE tnt_trust.did_documents SET revoked=true, revoked_at=NOW() WHERE did=:did")
    Mono<Void> revokeByDID(String did);

    @Modifying
    @Query("UPDATE tnt_trust.did_documents SET blockchain_tx_hash=:txHash WHERE did=:did")
    Mono<Void> updateTxHash(String did, String txHash);
}

// ============================================================
// Adapter
// ============================================================

/**
 * Persistence Adapter — {@code DIDRepositoryAdapter}.
 *
 * <p>Implements {@link DIDRepository} by delegating to {@link DIDDocumentR2dbcRepository}.
 *
 * @author MANFOUO Braun
 */
@Component
public class DIDRepositoryAdapter implements DIDRepository {

    private final DIDDocumentR2dbcRepository r2dbcRepo;

    public DIDRepositoryAdapter(final DIDDocumentR2dbcRepository r2dbcRepo) {
        this.r2dbcRepo = r2dbcRepo;
    }

    @Override
    public Mono<DIDDocument> save(final DIDDocument doc) {
        return r2dbcRepo.save(DIDDocumentEntity.fromDomain(doc)).map(DIDDocumentEntity::toDomain);
    }

    @Override
    public Mono<DIDDocument> findByActorId(final String actorId, final String tenantId) {
        return r2dbcRepo.findActiveByActorId(actorId, tenantId).map(DIDDocumentEntity::toDomain);
    }

    @Override
    public Mono<DIDDocument> findByDID(final String did) {
        return r2dbcRepo.findById(did).map(DIDDocumentEntity::toDomain);
    }

    @Override
    public Mono<Void> revokeByDID(final String did) {
        return r2dbcRepo.revokeByDID(did);
    }

    @Override
    public Flux<DIDDocument> findActiveByTenantId(final String tenantId) {
        return r2dbcRepo.findActiveByTenantId(tenantId).map(DIDDocumentEntity::toDomain);
    }
}
