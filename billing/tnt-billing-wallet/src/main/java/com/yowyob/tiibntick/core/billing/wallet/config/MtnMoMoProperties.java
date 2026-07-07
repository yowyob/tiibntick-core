package com.yowyob.tiibntick.core.billing.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MTN MoMo Collections API configuration properties.
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "tnt.billing.wallet.mtn")
public record MtnMoMoProperties(
        String baseUrl,
        String userId,
        String apiKey,
        String subscriptionKey,
        /** sandbox | production */
        String environment,
        String webhookSecret
) {}
