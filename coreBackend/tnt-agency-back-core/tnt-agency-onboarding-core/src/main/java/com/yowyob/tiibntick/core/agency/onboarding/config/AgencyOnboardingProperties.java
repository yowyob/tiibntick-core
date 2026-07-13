package com.yowyob.tiibntick.core.agency.onboarding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * TiiBnTick Core onboarding provisioning triggered when agency onboarding is approved.
 */
@ConfigurationProperties(prefix = "tnt.core.onboarding")
public class AgencyOnboardingProperties {

    /**
     * When true, applies {@link #commercialPlan} then {@link #financeServiceCodes} on the Kernel org.
     */
    private boolean provisionCommercialServices = true;

    /** Commercial plan for facturation (COMMERCIAL + PRODUCT + SALES + BILLING). */
    private String commercialPlan = "COMMERCE";

    /**
     * Finance services subscribed individually after the commercial plan
     * (a second commercial plan would replace the first).
     */
    private List<String> financeServiceCodes = List.of(
            "ACCOUNTING", "BANKING", "TREASURY", "CASHIER");

    /** HR / payroll (HRM app) — subscribed individually after the commercial plan. */
    private List<String> hrmServiceCodes = List.of("HRM", "PAYROLL");

    private long serviceQuotaLimit = 10_000L;
    private long serviceQuotaWindowSeconds = 3_600L;

    public boolean isProvisionCommercialServices() {
        return provisionCommercialServices;
    }

    public void setProvisionCommercialServices(boolean provisionCommercialServices) {
        this.provisionCommercialServices = provisionCommercialServices;
    }

    public String getCommercialPlan() {
        return commercialPlan;
    }

    public void setCommercialPlan(String commercialPlan) {
        this.commercialPlan = commercialPlan;
    }

    public List<String> getFinanceServiceCodes() {
        return financeServiceCodes;
    }

    public void setFinanceServiceCodes(List<String> financeServiceCodes) {
        this.financeServiceCodes = financeServiceCodes;
    }

    public List<String> getHrmServiceCodes() {
        return hrmServiceCodes;
    }

    public void setHrmServiceCodes(List<String> hrmServiceCodes) {
        this.hrmServiceCodes = hrmServiceCodes;
    }

    public long getServiceQuotaLimit() {
        return serviceQuotaLimit;
    }

    public void setServiceQuotaLimit(long serviceQuotaLimit) {
        this.serviceQuotaLimit = serviceQuotaLimit;
    }

    public long getServiceQuotaWindowSeconds() {
        return serviceQuotaWindowSeconds;
    }

    public void setServiceQuotaWindowSeconds(long serviceQuotaWindowSeconds) {
        this.serviceQuotaWindowSeconds = serviceQuotaWindowSeconds;
    }
}
