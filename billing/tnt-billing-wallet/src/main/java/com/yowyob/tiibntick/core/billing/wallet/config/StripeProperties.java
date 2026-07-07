package com.yowyob.tiibntick.core.billing.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Stripe payment API configuration.
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "tnt.billing.wallet.stripe")
public record StripeProperties(
        String secretKey,
        String publishableKey,
        String webhookSecret
) {}
