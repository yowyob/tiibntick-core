package com.yowyob.tiibntick.core.inventory.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Spring autoconfiguration entry point for tnt-inventory-core.
 *
 * <p>Activates component scanning over the inventory package so that all
 * {@code @Service}, {@code @Component}, and {@code @Repository} beans are
 * discovered when this module is included in a host application.</p>
 *
 * <p>Also enables Spring Data R2DBC repositories for the inventory persistence layer,
 * including the newly added {@code InventoryMovementR2dbcRepository} and
 * {@code InventoryAlertR2dbcRepository} that replace the former in-memory stores.</p>
 *
 * @author MANFOUO Braun
 */
@Configuration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.inventory")
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.inventory.adapter.out.persistence.repository")
public class InventoryCoreConfig {
    // Intentionally empty — all beans discovered via @ComponentScan and @EnableR2dbcRepositories.
}
