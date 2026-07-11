package com.yowyob.tiibntick.core.trust.application.port.out;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.CustodyTransferRecord;

/**
 * Outbound Port — {@code CustodyTransferCacheRepository}.
 *
 * <p>Local PostgreSQL cache for {@link CustodyTransferRecord} instances.
 * Provides fast query access to the package chain of custody
 * without hitting the Fabric ledger on every read.
 *
 * <p>Targets the {@code tnt_trust.custody_transfers} table.
 *
 * <p><strong>v1.1:</strong> Added {@link #findLatestConfirmedHashByParcelId(String)}
 * to support {@code IBlockchainAuditPort.getParcelChainTailHash()} in
 * {@link com.yowyob.tiibntick.core.trust.adapter.out.incident.IncidentBlockchainAuditAdapter}.
 *
 * @author MANFOUO Braun
 * @version 1.1
 */
public interface CustodyTransferCacheRepository {

    /**
     * Saves or updates a custody transfer record.
     *
     * @param transfer the transfer to persist
     * @return a {@link Mono} emitting the saved transfer
     */
    Mono<CustodyTransferRecord> save(CustodyTransferRecord transfer);

    /**
     * Finds all custody transfers for a package by its tracking code, oldest first.
     *
     * @param trackingCode the package tracking code
     * @param tenantId     the tenant identifier
     * @return a {@link Flux} of custody transfers, oldest first
     */
    Flux<CustodyTransferRecord> findByTrackingCode(String trackingCode, String tenantId);

    /**
     * Finds all custody transfers for a package by its internal ID, oldest first.
     *
     * @param packageId the package identifier
     * @param tenantId  the tenant identifier
     * @return a {@link Flux} of custody transfers, oldest first
     */
    Flux<CustodyTransferRecord> findByPackageId(String packageId, String tenantId);

    /**
     * Updates the Fabric transaction hash for a transfer after on-chain confirmation.
     * Called by {@link com.yowyob.tiibntick.core.trust.adapter.in.kafka.TrustCommittedEventConsumer}.
     *
     * @param transferId the transfer identifier
     * @param txHash     the Fabric transaction hash
     * @return a {@link Mono} completing when the update is persisted
     */
    Mono<Void> updateTxHash(String transferId, String txHash);

    /**
     * Retrieves the most recent confirmed (non-null) blockchain tx hash for a parcel.
     *
     * <p>Fallback used by
     * {@link com.yowyob.tiibntick.core.trust.adapter.out.incident.IncidentBlockchainAuditAdapter}
     * when no delivery proof is found. Returns the latest confirmed custody transfer hash
     * for the parcel, or empty if none exists.
     *
     * @param parcelId the parcel (package) identifier
     * @return a {@link Mono} emitting the latest confirmed tx hash, or empty if none
     */
    Mono<String> findLatestConfirmedHashByParcelId(String parcelId);
}
