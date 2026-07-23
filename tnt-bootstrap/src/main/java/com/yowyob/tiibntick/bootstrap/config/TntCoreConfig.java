package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.kernel.event.config.YowEventKernelAutoConfiguration;
import com.yowyob.tiibntick.bootstrap.registry.TntExtensionRegistry;
import com.yowyob.tiibntick.bootstrap.registry.TntRoleRegistrar;
import com.yowyob.tiibntick.bootstrap.registry.TntSettingsRegistrar;
import com.yowyob.tiibntick.core.gofp.config.GoFreelancerPointCoreConfig;
import com.yowyob.tiibntick.core.marketback.config.MarketBackCoreConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Central Spring configuration for TiiBnTick Core.
 *
 * <p>Imports and wires all cross-cutting configuration components:
 * role registrar, settings registrar, extension registry, module registry,
 * AOP configuration (for @RequirePermission from tnt-roles-core).
 *
 * <p> — Added:
 * <ul>
 *   <li>{@link TntAopConfiguration} — enables AspectJ auto-proxy for
 *       {@code @RequirePermission} AOP aspect from {@code tnt-roles-core}.</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Configuration
@Import({
        ApplicationProfileConfig.class,
        TntDataSourceConfig.class,
        LiquibaseConfig.class,
        TntSchedulerLockConfig.class,
        TntSecurityConfig.class,
        TntAopConfiguration.class,
        TntWebFluxConfig.class,
        TntKafkaConfig.class,
        TntKafkaTopicsConfig.class,
        TntOpenApiConfig.class,
        TntMetricsConfig.class,
        TntActuatorConfig.class,
        GoFreelancerPointCoreConfig.class,  // ← L6 Core Backend Market
        YowEventKernelAutoConfiguration.class // ← L0 event bus (Chantier C · Audit n°3 P1: reactivated 2026-07-18)
})
public class TntCoreConfig {

    @Bean
    public TntRoleRegistrar tntRoleRegistrar() {
        return new TntRoleRegistrar();
    }

    @Bean
    public TntSettingsRegistrar tntSettingsRegistrar() {
        return new TntSettingsRegistrar();
    }

    @Bean
    public TntExtensionRegistry tntExtensionRegistry() {
        return new TntExtensionRegistry();
    }

    @Bean
    public TntModuleRegistry tntModuleRegistry() {
        return new TntModuleRegistry();
    }

    /*@Bean
    public TiiBnTickApplicationContext tntApplicationContext() {
        return new TiiBnTickApplicationContext();
    }*/

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public SolutionContext tntSolutionContext(ApplicationProfile profile) {
        return SolutionContext.builder()
                .solutionCode(SolutionContext.TNT_CODE)
                .solutionName(SolutionContext.TNT_NAME)
                .version("0.0.1")
                .activeProfile(profile)
                .build();
    }
}
