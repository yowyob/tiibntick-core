package com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.billing.pricing.domain.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@Slf4j
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.billing.pricing.infrastructure.adapter.in.web")
public class BillingPricingExceptionHandler {

    @ExceptionHandler(BillingPolicyNotFoundException.class)
    public ProblemDetail handleNotFound(BillingPolicyNotFoundException ex) {
        log.warn("BillingPolicy not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        pd.setType(URI.create("https://tiibntick.io/problems/billing-policy-not-found"));
        pd.setTitle("Billing Policy Not Found");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(NoPricingRuleMatchException.class)
    public ProblemDetail handleNoMatch(NoPricingRuleMatchException ex) {
        log.warn("No pricing rule matched: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        pd.setType(URI.create("https://tiibntick.io/problems/no-pricing-rule-match"));
        pd.setTitle("No Pricing Rule Matched");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(PolicyNotActiveException.class)
    public ProblemDetail handleNotActive(PolicyNotActiveException ex) {
        log.warn("Policy not active: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        pd.setType(URI.create("https://tiibntick.io/problems/policy-not-active"));
        pd.setTitle("Policy Not Active");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    @ExceptionHandler(InvalidPolicyException.class)
    public ProblemDetail handleInvalid(InvalidPolicyException ex) {
        log.warn("Invalid policy: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("https://tiibntick.io/problems/invalid-policy"));
        pd.setTitle("Invalid Billing Policy");
        pd.setDetail(ex.getMessage());
        return pd;
    }
}
