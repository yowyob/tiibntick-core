package com.yowyob.tiibntick.common.tenant;

import reactor.core.publisher.Mono;

/**
 * Resolves the {@link TenantContext} of the current reactive request.
 *
 * <p>Local replacement for the Kernel's {@code CurrentTenantUseCase} port — TiiBnTick
 * no longer shares Kernel Spring beans/types (see root {@code CLAUDE.md}: Kernel is
 * HTTP-only). Implemented in {@code tnt-bootstrap} by reading {@code TntSecurityContext}
 * off the reactive security context; consumed by {@link
 * com.yowyob.tiibntick.common.aop.TenantValidationAspect} to enforce {@code @TenantScoped}.
 *
 * @author MANFOUO Braun
 */
public interface CurrentTenantUseCase {

    Mono<TenantContext> currentTenant();
}
