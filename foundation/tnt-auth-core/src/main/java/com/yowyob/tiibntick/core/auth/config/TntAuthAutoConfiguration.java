package com.yowyob.tiibntick.core.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.auth.adapter.in.web.ReactiveSecurityContextExtractor;
import com.yowyob.tiibntick.core.auth.adapter.out.kernel.NoOpYowAuthTntAdapter;
import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.application.port.in.ValidateTokenUseCase;
import com.yowyob.tiibntick.core.auth.application.port.out.IYowAuthTntAdapter;
import com.yowyob.tiibntick.core.auth.application.service.KernelPublicKeyProvider;
import com.yowyob.tiibntick.core.auth.application.service.TntJwtValidator;
import com.yowyob.tiibntick.core.auth.application.service.TntSecurityContextService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring Boot auto-configuration for tnt-auth-core.
 *
 * <p>REFACTORED (2026-06-15): No longer depends on UserSessionTokenService
 * from the Kernel. JWT validation is now performed locally.
 *
 * @author MANFOUO Braun
 */
@AutoConfiguration
@EnableConfigurationProperties(TntAuthProperties.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@Import(TntWebFluxConfiguration.class)
public class TntAuthAutoConfiguration {

    /**
     * No-op fallback adapter. Replaced by tnt-actor-core's real implementation
     * when that module is on the classpath.
     */
    @Bean
    //@ConditionalOnMissingBean(IYowAuthTntAdapter.class)
    @Primary
    public NoOpYowAuthTntAdapter noOpYowAuthTntAdapter() {
        return new NoOpYowAuthTntAdapter();
    }

    /**
     * Dedicated ObjectMapper for tnt-auth-core to avoid ambiguity
     * when multiple ObjectMapper beans exist in the context.
     */
    @Bean("tntAuthObjectMapper")
    @ConditionalOnMissingBean(name = "tntAuthObjectMapper")
    public ObjectMapper tntAuthObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean(KernelPublicKeyProvider.class)
    public KernelPublicKeyProvider kernelPublicKeyProvider(
            WebClient kernelWebClient,
            @Qualifier("tntAuthObjectMapper") ObjectMapper objectMapper) {
        return new KernelPublicKeyProvider(kernelWebClient, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(TntJwtValidator.class)
    public TntJwtValidator tntJwtValidator(KernelPublicKeyProvider publicKeyProvider) {
        return new TntJwtValidator(publicKeyProvider);
    }

    @Bean
    @ConditionalOnMissingBean({ResolveCurrentUserUseCase.class, ValidateTokenUseCase.class})
    public TntSecurityContextService tntSecurityContextService(
            TntJwtValidator jwtValidator,
            IYowAuthTntAdapter yowAuthTntAdapter) {
        return new TntSecurityContextService(jwtValidator, yowAuthTntAdapter);
    }

    /**
     * Programmatic reactive helper — inject in services instead of using
     * ReactiveSecurityContextHolder directly.
     */
    @Bean
    @ConditionalOnMissingBean(ReactiveSecurityContextExtractor.class)
    public ReactiveSecurityContextExtractor reactiveSecurityContextExtractor(
            ResolveCurrentUserUseCase resolveCurrentUserUseCase) {
        return new ReactiveSecurityContextExtractor(resolveCurrentUserUseCase);
    }
}
