package com.yowyob.tiibntick.core.gofreelancer.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Spring configuration entry point for the tnt-go-freelancer-point-back-core module.
 *
 * <p>Imported by {@code TntCoreConfig} in tnt-bootstrap to wire this module
 * into the shared application context.
 *
 * <p>Activates:
 * <ul>
 *   <li>Component scan over all beans in the module (services, adapters, controllers)</li>
 *   <li>R2DBC repository scanning for the persistence layer</li>
 * </ul>
 *
 * @author François-Charles ATANGA
 */
@Configuration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.gofreelancer")
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository"
)
public class GoFreelancerPointCoreConfig {
}
