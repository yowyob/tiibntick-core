package com.yowyob.tiibntick.bootstrap.health;

import com.yowyob.tiibntick.core.trust.adapter.out.health.TrustEventHealthIndicator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.CompositeReactiveHealthContributor;
import org.springframework.boot.health.contributor.ReactiveHealthContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Central Spring configuration that wires and registers all TiiBnTick health indicators
 * into a composite contributor exposed at {@code /actuator/health}.
 *
 * <p>Health groups configured in {@code application.yml}:
 * <ul>
 *   <li>{@code liveness} — livenessState only</li>
 *   <li>{@code readiness} — readinessState, db, redis, kafka</li>
 * </ul>
 *
 * <p>All TiiBnTick-specific indicators are exposed under
 * {@code /actuator/health/tnt-infra/*}:
 * <ul>
 *   <li>{@code kernel} — Yowyob Kernel (RT-comops) connectivity</li>
 *   <li>{@code database-pyramid} — PostgreSQL connectivity for all DB levels</li>
 *   <li>{@code minio} — MinIO object storage connectivity</li>
 *   <li>{@code or-tools} — Google OR-Tools 9.8.3296 native library availability</li>
 *   <li>{@code auth} — tnt-auth-core (L1) configuration health</li>
 *   <li>{@code roles} — tnt-roles-core (L1) registry health</li>
 *   <li>{@code trust-event-gateway} — tnt-trust-core's yow-trust-event link health</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Configuration
public class TntHealthConfig {

    /**
     * Composite contributor aggregating all TNT-specific health indicators.
     * These appear under {@code /actuator/health/tnt-infra/*}.
     *
     * <p>The {@code auth} and {@code roles} indicators were added to monitor the
     * L1 Foundation modules ({@code tnt-auth-core} and {@code tnt-roles-core}).
     *
     * <p>{@code trustEventHealthIndicator} is also auto-registered at the top
     * level as {@code /actuator/health/trustEventGateway} (its bean name) —
     * embedding it here too just gives it a place in the aggregated dashboard,
     * consistent with how {@code kernel}/{@code auth}/{@code roles} are exposed
     * twice today.
     *
     * @param kernelHealthIndicator      Kernel connectivity indicator
     * @param pyramidHealthIndicator     PostgreSQL pyramid indicator
     * @param minioHealthIndicator       MinIO object storage indicator
     * @param orToolsHealthIndicator     OR-Tools native library indicator
     * @param authHealthIndicator        tnt-auth-core configuration indicator
     * @param rolesHealthIndicator       tnt-roles-core registry indicator
     * @param trustEventHealthIndicator  tnt-trust-core yow-trust-event gateway indicator, absent when trust is disabled
     * @return composite contributor registered under {@code tnt-infra}
     */
    @Bean("tnt-infra")
    public ReactiveHealthContributor tntInfraHealthContributor(
            TntKernelHealthIndicator kernelHealthIndicator,
            TntDatabasePyramidHealthIndicator pyramidHealthIndicator,
            MinioHealthIndicator minioHealthIndicator,
            TntOrToolsHealthIndicator orToolsHealthIndicator,
            TntAuthHealthIndicator authHealthIndicator,
            TntRolesHealthIndicator rolesHealthIndicator,
            Optional<TrustEventHealthIndicator> trustEventHealthIndicator) {

        Map<String, ReactiveHealthContributor> indicators = new LinkedHashMap<>();
        indicators.put("kernel",            kernelHealthIndicator);
        indicators.put("database-pyramid",  pyramidHealthIndicator);
        indicators.put("minio",             minioHealthIndicator);
        indicators.put("or-tools",          orToolsHealthIndicator);
        // L1 Foundation module health indicators (added with tnt-auth-core + tnt-roles-core)
        indicators.put("auth",              authHealthIndicator);
        indicators.put("roles",             rolesHealthIndicator);
        // L6 Trust — yow-trust-event gateway (§15.6 of TNT_CORE_Connexion_Trust_Module.md).
        // Optional: absent when tnt.trust.enabled=false (TntTrustAutoConfiguration disabled).
        trustEventHealthIndicator.ifPresent(i -> indicators.put("trust-event-gateway", i));

        log.info("TiiBnTick infra health composite registered — {} indicators", indicators.size());
        return CompositeReactiveHealthContributor.fromMap(indicators);
    }
}
