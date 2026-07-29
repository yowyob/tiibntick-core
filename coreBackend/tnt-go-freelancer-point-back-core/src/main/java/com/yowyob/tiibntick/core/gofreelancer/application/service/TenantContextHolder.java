package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Helper that resolves the current tenant UUID from the reactive security context.
 *
 * <p>Replaces every hardcoded {@code UUID.fromString("00000000-0000-0000-0000-000000000001")}
 * scattered across the module's application services. Falls back to the default system tenant
 * only for Kafka-listener-driven flows (no HTTP context, tenantId read from event payload).
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantContextHolder {

    /** System tenant — used only for background/Kafka flows without an HTTP security context. */
    public static final UUID SYSTEM_TENANT =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    /**
     * Resolves the tenant UUID from the current reactive security context.
     * Falls back to {@link #SYSTEM_TENANT} if the request is unauthenticated
     * (e.g. Kafka listener, scheduled task).
     */
    public Mono<UUID> currentTenantId() {
        return resolveCurrentUserUseCase.resolveCurrentContextOrAnonymous()
                .map(ctx -> {
                    if (ctx.tenantId() != null) {
                        return ctx.tenantId();
                    }
                    log.debug("No tenant in security context — falling back to SYSTEM_TENANT");
                    return SYSTEM_TENANT;
                });
    }

    /**
     * Returns {@link #SYSTEM_TENANT} for use in non-reactive (Kafka consumer) contexts.
     * Prefer {@link #currentTenantId()} in reactive flows.
     */
    public static UUID systemTenant() {
        return SYSTEM_TENANT;
    }
}
