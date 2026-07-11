package com.yowyob.tiibntick.core.trust.application.port.in;

import reactor.core.publisher.Mono;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.BillingPolicyRecord;

/**
 * Inbound Port — {@code RecordBillingPolicyUseCase}.
 *
 * <p>Anchors a billing policy activation on Hyperledger Fabric.
 * Called by {@code tnt-billing-pricing} when a new pricing policy is activated.
 * The on-chain record serves as immutable proof of the tariff terms in effect,
 * enabling transparent dispute resolution under the OHADA framework.
 *
 * <p>Implemented by {@link com.yowyob.tiibntick.core.trust.application.service.BillingPolicyChainService}.
 *
 * @author MANFOUO Braun
 * @version 1.0
 */
public interface RecordBillingPolicyUseCase {

    /**
     * Anchors a billing policy activation on the blockchain.
     *
     * @param agencyId          the agency activating the policy
     * @param policyId          the billing policy identifier
     * @param tenantId          the tenant identifier
     * @param policySummaryJson JSON summary of the activated pricing rules
     * @return a {@link Mono} emitting the Fabric transaction hash (as correlation ID)
     */
    Mono<String> record(String agencyId, String policyId,
                        String tenantId, String policySummaryJson);

    /**
     * Retrieves the on-chain record for a billing policy to confirm it
     * was recorded on the Hyperledger Fabric ledger.
     *
     * @param policyId the billing policy identifier
     * @param tenantId the tenant identifier
     * @return a {@link Mono} emitting {@code true} if confirmed on-chain
     */
    Mono<Boolean> isRecordedOnChain(String policyId, String tenantId);
}
