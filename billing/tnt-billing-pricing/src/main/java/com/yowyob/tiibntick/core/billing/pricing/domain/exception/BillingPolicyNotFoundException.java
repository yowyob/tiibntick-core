package com.yowyob.tiibntick.core.billing.pricing.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)

public class BillingPolicyNotFoundException extends RuntimeException {
    public BillingPolicyNotFoundException(UUID policyId) {
        super("BillingPolicy not found: " + policyId);
    }
    public BillingPolicyNotFoundException(String message) {
        super(message);
    }
}
