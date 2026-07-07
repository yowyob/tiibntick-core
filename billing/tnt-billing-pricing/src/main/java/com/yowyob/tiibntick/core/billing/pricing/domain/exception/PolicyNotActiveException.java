package com.yowyob.tiibntick.core.billing.pricing.domain.exception;

import java.util.UUID;

public class PolicyNotActiveException extends RuntimeException {
    public PolicyNotActiveException(UUID policyId) {
        super("BillingPolicy is not active: " + policyId);
    }
}
