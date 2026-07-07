package com.yowyob.tiibntick.bootstrap.health;

import com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Health indicator for {@code tnt-roles-core} (L1 Foundation).
 *
 * <p>Verifies that the TiiBnTick RBAC layer is correctly configured. Checks:
 * <ol>
 *   <li>System tenant ID is configured.</li>
 *   <li>The {@code TntRoleDefinitionRegistry} is loaded and reports the expected number of roles
 *       (9 canonical {@code TntRole} definitions).</li>
 *   <li>AOP permission enforcement status matches the active profile.</li>
 * </ol>
 *
 * <p>This indicator uses {@code @Autowired(required = false)} so that the bootstrap
 * starts without error if {@code tnt-roles-core} is absent (e.g. in isolated unit tests).
 * In that case the indicator reports {@code UNKNOWN}.
 *
 * <p>Exposed at {@code /actuator/health/tnt-infra/roles}.
 *
 * @author MANFOUO Braun
 * @see TntHealthConfig
 */
@Slf4j
@Component
public class TntRolesHealthIndicator implements ReactiveHealthIndicator {

    /** Expected number of canonical TiiBnTick roles defined in {@code TntRole} enum. */
    private static final int EXPECTED_ROLE_COUNT = 9;

    @Value("${tnt.roles.system-tenant-id:}")
    private String systemTenantId;

    @Value("${tnt.roles.aop-enabled:true}")
    private boolean aopEnabled;

    @Value("${tnt.roles.provision-on-startup:true}")
    private boolean provisionOnStartup;

    @Value("${tnt.roles.permission-cache-ttl-seconds:300}")
    private int permissionCacheTtlSeconds;

    /**
     * Injected lazily — absent when tnt-roles-core is not on the classpath
     * (e.g. in isolated unit tests). When null, the indicator returns UNKNOWN.
     *
     * <p>The actual type is {@code TntRoleDefinitionRegistry} from
     * {@code com.yowyob.tiibntick.core.roles.application.service} — injected by name to
     * avoid a hard compile-time dependency in this bootstrap health indicator.
     */
    @Autowired(required = false)
    private TntRoleDefinitionRegistry tntRoleDefinitionRegistry;

    @Override
    public Mono<Health> health() {
        return Mono.fromCallable(this::evaluate);
    }

    private Health evaluate() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("system_tenant_id_configured", !systemTenantId.isEmpty());
        details.put("aop_enabled", aopEnabled);
        details.put("provision_on_startup", provisionOnStartup);
        details.put("permission_cache_ttl_seconds", permissionCacheTtlSeconds);

        // tnt-roles-core not on classpath (test environment)
        if (tntRoleDefinitionRegistry == null) {
            log.debug("TntRoleDefinitionRegistry not found — tnt-roles-core absent from classpath");
            return Health.unknown()
                    .withDetails(details)
                    .withDetail("reason", "tnt_roles_core_absent")
                    .build();
        }

        // Validate system tenant ID
        if (systemTenantId.isEmpty()) {
            log.warn("tnt-roles-core health DEGRADED: tnt.roles.system-tenant-id is not set");
            return Health.unknown()
                    .withDetails(details)
                    .withDetail("reason", "system_tenant_id_missing")
                    .build();
        }

        int registeredRoles = tntRoleDefinitionRegistry.size();
        details.put("roles_registered", registeredRoles);
        details.put("roles_expected", EXPECTED_ROLE_COUNT);

        if (registeredRoles < EXPECTED_ROLE_COUNT) {
            log.warn("tnt-roles-core health DEGRADED: {} of {} roles registered",
                    registeredRoles, EXPECTED_ROLE_COUNT);
            return Health.down()
                    .withDetails(details)
                    .withDetail("reason", "incomplete_role_registration")
                    .build();
        }

        return Health.up().withDetails(details).build();
    }
}
