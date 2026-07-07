package com.yowyob.tiibntick.core.auth.config;

import com.yowyob.tiibntick.core.auth.adapter.in.web.TntCurrentUserArgumentResolver;
import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;

/**
 * WebFlux-specific configuration that registers the {@link TntCurrentUserArgumentResolver}.
 * Separated from {@link TntAuthAutoConfiguration} to allow conditional loading
 * only in reactive web environments.
 *
 * @author MANFOUO Braun
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class TntWebFluxConfiguration implements WebFluxConfigurer {

    private final ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    public TntWebFluxConfiguration(ResolveCurrentUserUseCase resolveCurrentUserUseCase) {
        this.resolveCurrentUserUseCase = resolveCurrentUserUseCase;
    }

    @Override
    public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
        configurer.addCustomResolver(new TntCurrentUserArgumentResolver(resolveCurrentUserUseCase));
    }
}
