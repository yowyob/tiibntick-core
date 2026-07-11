package com.yowyob.tiibntick.core.trust.adapter.out.billing;

import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.BillingPolicyAnchorPayload;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.out.BillingPolicyAnchorPort;
import com.yowyob.tiibntick.core.trust.application.port.in.RecordBillingPolicyUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * tnt-trust-core implementation of {@link BillingPolicyAnchorPort} (outbound port
 * owned by tnt-billing-pricing).
 *
 * <p>tnt-trust-core depends on tnt-billing-pricing (one-directional, no Maven cycle —
 * billing-pricing never depends back on trust) purely to see this port and its payload
 * type; it delegates to the pre-existing {@link RecordBillingPolicyUseCase}.
 *
 * @author MANFOUO Braun
 * @see BillingPolicyAnchorPort
 */
@Component
@RequiredArgsConstructor
public class BillingPolicyAnchorAdapter implements BillingPolicyAnchorPort {

    private final RecordBillingPolicyUseCase recordBillingPolicy;

    @Override
    public Mono<Void> anchor(BillingPolicyAnchorPayload payload) {
        return recordBillingPolicy.record(
                        payload.ownerActorId(),
                        payload.policyId().toString(),
                        payload.tenantId().toString(),
                        payload.policySummaryJson())
                .then();
    }
}
