package com.yowyob.tiibntick.core.roles.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * Enables Spring Data R2DBC repositories in the roles persistence adapter package
 * (Chantier D · Audit n°6 · S5 — local RBAC persistence), same pattern as
 * {@code TntPlatformGatewayR2dbcConfig}.
 *
 * @author MANFOUO Braun
 */
@Configuration
@EnableR2dbcRepositories(basePackages = {
        "com.yowyob.tiibntick.core.roles.adapter.out.persistence"
})
public class TntRolesR2dbcConfig {
}
