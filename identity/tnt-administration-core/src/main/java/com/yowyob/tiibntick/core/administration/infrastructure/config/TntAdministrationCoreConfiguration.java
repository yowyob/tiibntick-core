package com.yowyob.tiibntick.core.administration.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Auto-configuration entry point for {@code tnt-administration-core}.
 *
 * <p>Registers:
 * <ul>
 *   <li>Component scan for the full administration package tree.</li>
 *   <li>R2DBC repositories for persistence adapters.</li>
 *   <li>AspectJ auto-proxy for {@code @RequirePermission} AOP (tnt-roles-core).</li>
 * </ul>
 *
 * <p>The {@code kernelWebClient} bean is provided by {@code KernelBridgeConfig}
 * (tnt-bootstrap) — the single KernelBridge for the whole project.
 *
 * @author MANFOUO Braun
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.administration")
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.administration.adapter.out.persistence.repository"
)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class TntAdministrationCoreConfiguration {
}
