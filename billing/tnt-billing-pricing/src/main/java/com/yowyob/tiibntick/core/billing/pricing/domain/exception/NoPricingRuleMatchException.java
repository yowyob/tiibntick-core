package com.yowyob.tiibntick.core.billing.pricing.domain.exception;

import java.util.UUID;

public class NoPricingRuleMatchException extends RuntimeException {
    public NoPricingRuleMatchException(UUID policyId) {
        super("No PricingRule matched the given context for policy: " + policyId);
    }
}
