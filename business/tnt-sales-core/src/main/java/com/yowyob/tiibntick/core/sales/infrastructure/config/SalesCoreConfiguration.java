package com.yowyob.tiibntick.core.sales.infrastructure.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Auto-configuration entry point for tnt-sales-core.
 * Author: MANFOUO Braun
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.sales")
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.sales.adapter.out.persistence.repository"
)
public class SalesCoreConfiguration {
}
