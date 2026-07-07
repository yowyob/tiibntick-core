package com.yowyob.tiibntick.core.auth.adapter.in.web;

import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import org.springframework.core.MethodParameter;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * WebFlux argument resolver that injects the authenticated TiiBnTick user
 * into controller method parameters annotated with {@link CurrentUser}.
 *
 * <p>Supports:
 * <ul>
 *   <li>{@link TntUserIdentity} — lightweight projection (preferred for most controllers)</li>
 *   <li>{@link TntSecurityContext} — full security context (when roles/permissions checks needed)</li>
 * </ul>
 *
 * <p>When {@code @CurrentUser(required = false)} and no context is present,
 * returns {@code null} (for TntUserIdentity) or anonymous (for TntSecurityContext)
 * without raising an error.
 *
 * <p>Registered by {@link com.yowyob.tiibntick.core.auth.config.TntWebFluxConfiguration}.
 *
 * @author MANFOUO Braun
 */
public class TntCurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    public TntCurrentUserArgumentResolver(ResolveCurrentUserUseCase resolveCurrentUserUseCase) {
        this.resolveCurrentUserUseCase = resolveCurrentUserUseCase;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && isSupportedType(parameter.getParameterType());
    }

    @Override
    public Mono<Object> resolveArgument(
            MethodParameter parameter,
            BindingContext bindingContext,
            ServerWebExchange exchange) {

        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        boolean required = annotation == null || annotation.required();

        if (TntSecurityContext.class.isAssignableFrom(parameter.getParameterType())) {
            return required
                    ? resolveCurrentUserUseCase.resolveCurrentContext().cast(Object.class)
                    : resolveCurrentUserUseCase.resolveCurrentContextOrAnonymous().cast(Object.class);
        }

        if (TntUserIdentity.class.isAssignableFrom(parameter.getParameterType())) {
            return required
                    ? resolveCurrentUserUseCase.resolveCurrentIdentity().cast(Object.class)
                    : resolveCurrentUserUseCase.resolveCurrentContextOrAnonymous()
                            .map(TntUserIdentity::from)
                            .cast(Object.class);
        }

        return Mono.empty();
    }

    private boolean isSupportedType(Class<?> type) {
        return TntUserIdentity.class.isAssignableFrom(type)
                || TntSecurityContext.class.isAssignableFrom(type);
    }
}
