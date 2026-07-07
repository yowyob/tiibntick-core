package com.yowyob.tiibntick.core.product.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Auto-configuration entry point for tnt-product-core.
 *
 * <p>Registers:
 * <ul>
 *   <li>Component scan for the full product package tree.</li>
 *   <li>R2DBC repositories for persistence adapters.</li>
 * </ul>
 *
 * <p>The {@code kernelWebClient} bean is provided by {@code KernelBridgeConfig}
 * (tnt-bootstrap) — the single KernelBridge for the whole project.
 *
 * @author MANFOUO Braun
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.product")
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.product.adapter.out.persistence.repository"
)
public class ProductCoreAutoConfiguration {
}
