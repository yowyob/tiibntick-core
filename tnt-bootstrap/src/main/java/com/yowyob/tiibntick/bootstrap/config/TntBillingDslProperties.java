package com.yowyob.tiibntick.bootstrap.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the TiiBnTick Billing DSL engine ().
 *
 * <p>Bound from {@code application.yml} under the {@code tnt.billing} prefix.
 *
 * <p>Controls billing template catalog refresh and DSL complexity limits per actor type.
 *
 * <p>Example configuration:
 * <pre>{@code
 * tnt:
 *   billing:
 *     templates:
 *       catalog-refresh-cron: "0 0 4 * * *"
 *       default-currency: XAF
 *     dsl:
 *       max-nesting-level-simplified: 3
 *       max-rules-per-policy-simplified: 20
 *       max-rules-per-policy-full: 100
 * }</pre>
 *
 * @author MANFOUO Braun
 */
@Component
@ConfigurationProperties(prefix = "tnt.billing")
@Getter
@Setter
public class TntBillingDslProperties {

    /** Template catalog configuration. */
    private Templates templates = new Templates();

    /** DSL complexity limits. */
    private Dsl dsl = new Dsl();

    @Getter
    @Setter
    public static class Templates {
        /**
         * Cron expression for refreshing the billing policy template catalog.
         * Default: every day at 4 AM.
         */
        private String catalogRefreshCron = "0 0 4 * * *";

        /**
         * Default currency for billing templates.
         * Default: XAF (Central African Franc).
         */
        private String defaultCurrency = "XAF";
    }

    @Getter
    @Setter
    public static class Dsl {
        /**
         * Maximum rule nesting depth for SIMPLIFIED DSL access level.
         * Applies to FreelancerOrg OWNERs and PointOperators.
         * Default: 3.
         */
        private int maxNestingLevelSimplified = 3;

        /**
         * Maximum number of rules per billing policy for SIMPLIFIED DSL access level.
         * Default: 20.
         */
        private int maxRulesPerPolicySimplified = 20;

        /**
         * Maximum number of rules per billing policy for FULL DSL access level.
         * Applies to Agency managers and platform admins.
         * Default: 100.
         */
        private int maxRulesPerPolicyFull = 100;
    }
}
