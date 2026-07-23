package com.yowyob.tiibntick.core.trust.application.port.out;

import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.BillingPolicyRecord;

/**
 * Outbound Port — {@code BillingPolicyRecordRepository}.
 *
 * <p>Persistence contract for {@link BillingPolicyRecord} value objects.
 * Implemented by the R2DBC adapter targeting the
 * {@code tnt_trust.billing_policy_records} table in the {@code tnt_trust_db} database.
 *
 * <p>This repository is the local PostgreSQL cache for on-chain billing
 * policy activation data. It is updated asynchronously when
 * {@code yow.trust.events.committed} notifications arrive from {@code yow-trust-event}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface BillingPolicyRecordRepository {

    /**
     * Saves a new {@link BillingPolicyRecord} activation.
     *
     * @param record the record to persist
     * @return a {@link Mono} emitting the saved record
     */
    Mono<BillingPolicyRecord> save(BillingPolicyRecord record);

    /**
     * Updates the Fabric transaction hash for the most recent activation
     * record of a policy, after on-chain confirmation.
     * Called by {@link com.yowyob.tiibntick.core.trust.adapter.in.kafka.TrustCommittedEventConsumer}.
     *
     * @param policyId the billing policy identifier (matches the Kafka event's {@code entityId})
     * @param txHash   the Fabric transaction hash
     * @return a {@link Mono} completing when the update is persisted
     */
    Mono<Void> updateTxHash(String policyId, String txHash);
}
