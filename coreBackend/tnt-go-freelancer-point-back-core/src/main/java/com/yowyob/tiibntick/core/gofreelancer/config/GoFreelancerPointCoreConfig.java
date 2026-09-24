package com.yowyob.tiibntick.core.gofreelancer.config;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.chat.NoOpNegotiationChatAdapter;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.INegotiationChatPort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.util.Arrays;

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
@Slf4j
@Configuration
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.gofreelancer")
@EnableR2dbcRepositories(
        basePackages = "com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository"
)
public class GoFreelancerPointCoreConfig {

    @Autowired
    private Environment environment;

    @Value("${tnt.gofp.ownership-guard.enabled:true}")
    private boolean ownershipGuardEnabled;

    /**
     * Refuses to start when {@code tnt.gofp.ownership-guard.enabled=false} is combined with
     * an active {@code PROD} profile. Without this guard a stray/leaked
     * {@code TNT_GOFP_OWNERSHIP_GUARD_ENABLED=false} env var in production would silently grant
     * any authenticated caller access to every delivery need regardless of ownership.
     *
     * <p>Same fail-fast shape as {@code TntSecurityConfig#validateAnonymousContextNotAllowedInProd}
     * (Audit n°7 · #9) — cannot reuse {@code ApplicationProfile} directly since that enum lives
     * in {@code tnt-bootstrap} (L7) above this module (L6-Bis).
     */
    @PostConstruct
    void validateOwnershipGuardNotDisabledInProd() {
        boolean isProd = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "PROD".equalsIgnoreCase(p));
        if (!ownershipGuardEnabled && isProd) {
            throw new IllegalStateException(
                    "Refusing to start with profile PROD: tnt.gofp.ownership-guard.enabled=false "
                    + "(TNT_GOFP_OWNERSHIP_GUARD_ENABLED) would silently grant any authenticated "
                    + "caller access to all delivery needs regardless of ownership. "
                    + "Set TNT_GOFP_OWNERSHIP_GUARD_ENABLED=true (or unset it) for the prod profile.");
        }
    }

    /**
     * Fallback negotiation-chat adapter until a product BFF provides a real implementation.
     *
     * @author MANFOUO BRAUN
     */
    @Bean
    @ConditionalOnMissingBean(INegotiationChatPort.class)
    public INegotiationChatPort negotiationChatPort() {
        return new NoOpNegotiationChatAdapter();
    }
}
