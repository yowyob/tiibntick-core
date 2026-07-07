package com.yowyob.tiibntick.core.accounting.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Auto-configuration entry point for tnt-accounting-core.
 * Enables component scanning and R2DBC repositories for the accounting module.
 * Picked up by tnt-bootstrap via META-INF/spring/autoconfigure.imports.
 * Author: MANFOUO Braun
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.accounting")
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.accounting.adapter.out.persistence.repository"
)
public class AccountingCoreConfiguration {
    // All beans are registered via @Component / @Service / @Repository
    // within the scanned packages. No manual bean declarations needed here.
}
