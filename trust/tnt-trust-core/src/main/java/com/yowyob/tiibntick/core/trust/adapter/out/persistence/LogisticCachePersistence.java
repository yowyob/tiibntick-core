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
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;
import com.yowyob.tiibntick.core.trust.domain.model.enums.CustodyTransferType;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DeliveryProofRecord;
import com.yowyob.tiibntick.core.trust.application.port.out.CustodyTransferCacheRepository;
import com.yowyob.tiibntick.core.trust.application.port.out.DeliveryProofCacheRepository;

import java.time.LocalDateTime;

// ============================================================
// DeliveryProof: Entity + Repository + Adapter
// ============================================================

@Table(schema = "tnt_trust", name = "delivery_proofs")
class DeliveryProofEntity {

    @Id @Column("proof_id") private String proofId;
    @Column("mission_id") private String missionId;
    @Column("package_id") private String packageId;
    @Column("actor_id") private String actorId;
    @Column("tenant_id") private String tenantId;
    @Column("photo_hash") private String photoHash;
    @Column("signature_hash") private String signatureHash;
    @Column("gps_lat") private double gpsLat;
    @Column("gps_lng") private double gpsLng;
    @Column("confirmed_at") private LocalDateTime confirmedAt;
    @Column("blockchain_tx_hash") private String blockchainTxHash;

    DeliveryProofEntity() {}

    static DeliveryProofEntity fromDomain(final DeliveryProofRecord r) {
        final DeliveryProofEntity e = new DeliveryProofEntity();
        e.proofId = r.getProofId(); e.missionId = r.getMissionId();
        e.packageId = r.getPackageId(); e.actorId = r.getActorId();
        e.tenantId = r.getTenantId(); e.photoHash = r.getPhotoHash();
        e.signatureHash = r.getSignatureHash();
        e.gpsLat = r.getGpsLat(); e.gpsLng = r.getGpsLng();
        e.confirmedAt = r.getConfirmedAt(); e.blockchainTxHash = r.getBlockchainTxHash();
        return e;
    }

    DeliveryProofRecord toDomain() {
        final DeliveryProofRecord r = new DeliveryProofRecord(proofId, missionId, packageId,
                actorId, tenantId, photoHash, signatureHash, gpsLat, gpsLng, confirmedAt);
        if (blockchainTxHash != null) r.confirmOnChain(blockchainTxHash);
        return r;
    }

    public String getProofId() { return proofId; }
    public void setProofId(String v) { proofId = v; }
    public String getMissionId() { return missionId; }
    public void setMissionId(String v) { missionId = v; }
    public String getPackageId() { return packageId; }
    public void setPackageId(String v) { packageId = v; }
    public String getActorId() { return actorId; }
    public void setActorId(String v) { actorId = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { tenantId = v; }
    public String getPhotoHash() { return photoHash; }
    public void setPhotoHash(String v) { photoHash = v; }
    public String getSignatureHash() { return signatureHash; }
    public void setSignatureHash(String v) { signatureHash = v; }
    public double getGpsLat() { return gpsLat; }
    public void setGpsLat(double v) { gpsLat = v; }
    public double getGpsLng() { return gpsLng; }
    public void setGpsLng(double v) { gpsLng = v; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime v) { confirmedAt = v; }
    public String getBlockchainTxHash() { return blockchainTxHash; }
    public void setBlockchainTxHash(String v) { blockchainTxHash = v; }
}

@Repository
interface DeliveryProofR2dbcRepository extends ReactiveCrudRepository<DeliveryProofEntity, String> {
    @Query("SELECT * FROM tnt_trust.delivery_proofs WHERE mission_id=:missionId AND tenant_id=:tenantId ORDER BY confirmed_at ASC")
    Flux<DeliveryProofEntity> findByMissionId(String missionId, String tenantId);

    @Modifying
    @Query("UPDATE tnt_trust.delivery_proofs SET blockchain_tx_hash=:txHash WHERE proof_id=:proofId")
    Mono<Void> updateTxHash(String proofId, String txHash);

    /**
     * Retrieves the most recent confirmed blockchain_tx_hash for a parcel.
     * Used by IncidentBlockchainAuditAdapter to get the parcel chain tail hash.
     */
    @Query("""
            SELECT blockchain_tx_hash
            FROM tnt_trust.delivery_proofs
            WHERE package_id = :parcelId
              AND blockchain_tx_hash IS NOT NULL
            ORDER BY confirmed_at DESC
            LIMIT 1
            """)
    Mono<String> findLatestConfirmedHashByPackageId(String parcelId);
}

@Component
class DeliveryProofCacheRepositoryAdapter implements DeliveryProofCacheRepository {

    private final DeliveryProofR2dbcRepository repo;

    DeliveryProofCacheRepositoryAdapter(final DeliveryProofR2dbcRepository repo) { this.repo = repo; }

    @Override public Mono<DeliveryProofRecord> save(final DeliveryProofRecord r) {
        return repo.save(DeliveryProofEntity.fromDomain(r)).map(DeliveryProofEntity::toDomain); }
    @Override public Mono<DeliveryProofRecord> findByProofId(final String id) {
        return repo.findById(id).map(DeliveryProofEntity::toDomain); }
    @Override public Flux<DeliveryProofRecord> findByMissionId(final String mId, final String tId) {
        return repo.findByMissionId(mId, tId).map(DeliveryProofEntity::toDomain); }
    @Override public Mono<Void> updateTxHash(final String proofId, final String txHash) {
        return repo.updateTxHash(proofId, txHash); }
    /** {@inheritDoc} */
    @Override public Mono<String> findLatestConfirmedHashByParcelId(final String parcelId) {
        return repo.findLatestConfirmedHashByPackageId(parcelId); }
}

// ============================================================
// CustodyTransfer: Entity + Repository + Adapter
// ============================================================

@Table(schema = "tnt_trust", name = "custody_transfers")
class CustodyTransferEntity {

    @Id @Column("transfer_id") private String transferId;
    @Column("package_id") private String packageId;
    @Column("tracking_code") private String trackingCode;
    @Column("tenant_id") private String tenantId;
    @Column("from_actor_id") private String fromActorId;
    @Column("to_actor_id") private String toActorId;
    @Column("transfer_type") private String transferType;
    @Column("hub_id") private String hubId;
    @Column("gps_lat") private Double gpsLat;
    @Column("gps_lng") private Double gpsLng;
    @Column("transferred_at") private LocalDateTime transferredAt;
    @Column("poc_hash") private String pocHash;
    @Column("previous_custody_hash") private String previousCustodyHash;
    @Column("custody_hash") private String custodyHash;
    @Column("blockchain_tx_hash") private String blockchainTxHash;

    CustodyTransferEntity() {}

    static CustodyTransferEntity fromDomain(final CustodyTransferRecord r) {
        final CustodyTransferEntity e = new CustodyTransferEntity();
        e.transferId = r.getTransferId(); e.packageId = r.getPackageId();
        e.trackingCode = r.getTrackingCode(); e.tenantId = r.getTenantId();
        e.fromActorId = r.getFromActorId(); e.toActorId = r.getToActorId();
        e.transferType = r.getTransferType() != null ? r.getTransferType().name() : null;
        e.hubId = r.getHubId();
        e.gpsLat = r.getGpsLat(); e.gpsLng = r.getGpsLng();
        e.transferredAt = r.getTransferredAt();
        e.pocHash = r.getPocHash();
        e.previousCustodyHash = r.getPreviousCustodyHash();
        e.custodyHash = r.getCustodyHash();
        e.blockchainTxHash = r.getBlockchainTxHash();
        return e;
    }

    CustodyTransferRecord toDomain() {
        final CustodyTransferRecord r = new CustodyTransferRecord(
                transferId, packageId, trackingCode, tenantId,
                fromActorId, toActorId,
                transferType != null ? CustodyTransferType.valueOf(transferType) : null,
                hubId, gpsLat, gpsLng, transferredAt);
        r.setPocHash(pocHash);
        r.setPreviousCustodyHash(previousCustodyHash);
        r.setCustodyHash(custodyHash);
        if (blockchainTxHash != null) r.confirmOnChain(blockchainTxHash);
        return r;
    }

    public String getTransferId() { return transferId; }
    public void setTransferId(String v) { transferId = v; }
    public String getPackageId() { return packageId; }
    public void setPackageId(String v) { packageId = v; }
    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String v) { trackingCode = v; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String v) { tenantId = v; }
    public String getFromActorId() { return fromActorId; }
    public void setFromActorId(String v) { fromActorId = v; }
    public String getToActorId() { return toActorId; }
    public void setToActorId(String v) { toActorId = v; }
    public String getTransferType() { return transferType; }
    public void setTransferType(String v) { transferType = v; }
    public String getHubId() { return hubId; }
    public void setHubId(String v) { hubId = v; }
    public Double getGpsLat() { return gpsLat; }
    public void setGpsLat(Double v) { gpsLat = v; }
    public Double getGpsLng() { return gpsLng; }
    public void setGpsLng(Double v) { gpsLng = v; }
    public LocalDateTime getTransferredAt() { return transferredAt; }
    public void setTransferredAt(LocalDateTime v) { transferredAt = v; }
    public String getPocHash() { return pocHash; }
    public void setPocHash(String v) { pocHash = v; }
    public String getPreviousCustodyHash() { return previousCustodyHash; }
    public void setPreviousCustodyHash(String v) { previousCustodyHash = v; }
    public String getCustodyHash() { return custodyHash; }
    public void setCustodyHash(String v) { custodyHash = v; }
    public String getBlockchainTxHash() { return blockchainTxHash; }
    public void setBlockchainTxHash(String v) { blockchainTxHash = v; }
}

@Repository
interface CustodyTransferR2dbcRepository extends ReactiveCrudRepository<CustodyTransferEntity, String> {
    @Query("SELECT * FROM tnt_trust.custody_transfers WHERE tracking_code=:code AND tenant_id=:tenantId ORDER BY transferred_at ASC")
    Flux<CustodyTransferEntity> findByTrackingCode(String code, String tenantId);

    @Query("SELECT * FROM tnt_trust.custody_transfers WHERE package_id=:packageId AND tenant_id=:tenantId ORDER BY transferred_at ASC")
    Flux<CustodyTransferEntity> findByPackageId(String packageId, String tenantId);

    @Modifying
    @Query("UPDATE tnt_trust.custody_transfers SET blockchain_tx_hash=:txHash WHERE transfer_id=:transferId")
    Mono<Void> updateTxHash(String transferId, String txHash);

    /**
     * Retrieves the most recent confirmed blockchain_tx_hash for a parcel via custody transfers.
     * Fallback when no delivery proof exists.
     */
    @Query("""
            SELECT blockchain_tx_hash
            FROM tnt_trust.custody_transfers
            WHERE package_id = :parcelId
              AND blockchain_tx_hash IS NOT NULL
            ORDER BY transferred_at DESC
            LIMIT 1
            """)
    Mono<String> findLatestConfirmedHashByPackageId(String parcelId);
}

@Component
class CustodyTransferCacheRepositoryAdapter implements CustodyTransferCacheRepository {

    private final CustodyTransferR2dbcRepository repo;

    CustodyTransferCacheRepositoryAdapter(final CustodyTransferR2dbcRepository repo) { this.repo = repo; }

    @Override public Mono<CustodyTransferRecord> save(final CustodyTransferRecord r) {
        return repo.save(CustodyTransferEntity.fromDomain(r)).map(CustodyTransferEntity::toDomain); }
    @Override public Flux<CustodyTransferRecord> findByTrackingCode(final String code, final String tId) {
        return repo.findByTrackingCode(code, tId).map(CustodyTransferEntity::toDomain); }
    @Override public Flux<CustodyTransferRecord> findByPackageId(final String pId, final String tId) {
        return repo.findByPackageId(pId, tId).map(CustodyTransferEntity::toDomain); }
    @Override public Mono<Void> updateTxHash(final String transferId, final String txHash) {
        return repo.updateTxHash(transferId, txHash); }
    /** {@inheritDoc} */
    @Override public Mono<String> findLatestConfirmedHashByParcelId(final String parcelId) {
        return repo.findLatestConfirmedHashByPackageId(parcelId); }
}
