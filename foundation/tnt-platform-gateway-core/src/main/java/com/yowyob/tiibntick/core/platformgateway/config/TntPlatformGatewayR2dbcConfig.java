package com.yowyob.tiibntick.core.platformgateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Enables Spring Data R2DBC repositories in the platform-gateway persistence adapter
 * package — first L1/foundation module with its own persistence, see
 * {@code docs/auth/platform-client-management-design.md} §2.0/§3.
 *
 * @author MANFOUO Braun
 */
@Configuration
@EnableR2dbcRepositories(basePackages = {
        "com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence"
})
public class TntPlatformGatewayR2dbcConfig {
}
