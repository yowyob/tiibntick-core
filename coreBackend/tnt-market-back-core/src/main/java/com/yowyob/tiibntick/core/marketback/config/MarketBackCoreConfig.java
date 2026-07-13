package com.yowyob.tiibntick.core.marketback.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Spring configuration for tnt-market-back-core.
 *
 * <p>Imported by {@code TntCoreConfig} in tnt-bootstrap. Enables component
 * scanning for this module's services/controllers/adapters and imports
 * {@link MarketBackR2dbcConfig} for the module's dedicated {@code tnt_market}
 * schema connection factory.
 *
 * @author MANFOUO Braun
 */
@Configuration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.marketback")
@Import(MarketBackR2dbcConfig.class)
public class MarketBackCoreConfig {
}
