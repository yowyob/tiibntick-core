package com.yowyob.tiibntick.core.billing.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Orange Money Cameroon payment API configuration.
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "tnt.billing.wallet.orange")
public record OrangeMoneyProperties(
        String baseUrl,
        String merchantKey,
        String returnUrl,
        String cancelUrl,
        String notifUrl
) {}
