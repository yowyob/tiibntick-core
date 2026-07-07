package com.yowyob.tiibntick.core.auth.application.port.in;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import reactor.core.publisher.Mono;

/**
 * Primary (inbound) port: resolves the current authenticated user's identity
 * from the reactive security context populated by the Kernel filter chain.
 *
 * <p>TiiBnTick modules call this port instead of accessing
 * {@code ReactiveSecurityContextHolder} directly, keeping the Kernel types encapsulated.
 *
 * @author MANFOUO Braun
 */
public interface ResolveCurrentUserUseCase {

    /**
     * Returns the full TiiBnTick security context for the current reactive chain.
     * Emits {@link com.yowyob.tiibntick.core.auth.domain.exception.TntAuthException#missingContext()}
     * if no authenticated context is available.
     */
    Mono<TntSecurityContext> resolveCurrentContext();

    /**
     * Returns a lightweight projection of the current authenticated user's identity.
     * Preferred for controller parameter injection via {@code @CurrentUser}.
     */
    Mono<TntUserIdentity> resolveCurrentIdentity();

    /**
     * Returns the TiiBnTick security context or {@link TntSecurityContext#anonymous()}
     * if the request is unauthenticated. Never emits an error.
     */
    Mono<TntSecurityContext> resolveCurrentContextOrAnonymous();
}
