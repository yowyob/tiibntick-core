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
 * <p>Replaces every hardcoded system-tenant UUID
 * scattered across the module's application services. Falls back to the default system tenant
 * only for Kafka-listener-driven flows (no HTTP context, tenantId read from event payload).
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantContextHolder {

    /**
     * System tenant — used only for background/Kafka flows without an HTTP security context.
     * It is THE TiiBnTick system tenant ({@code TNT_SYSTEM_TENANT_ID}), shared with the roles
     * module — never a Go-Freelancer-specific value.
     */
    public static final UUID SYSTEM_TENANT = com.yowyob.tiibntick.common.tenant.SystemTenant.id();

    private final ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    /**
     * Resolves the tenant UUID from the current reactive security context.
     * Falls back to {@link #SYSTEM_TENANT} if the request is unauthenticated
     * (e.g. Kafka listener, scheduled task).
     */
    public Mono<UUID> currentTenantId() {
        return Mono.deferContextual(view -> view.hasKey(TENANT_OVERRIDE)
                ? Mono.just(view.<UUID>get(TENANT_OVERRIDE))
                : callerTenantId());
    }

    /**
     * Lot C-56 — runs {@code work} as if the current tenant were {@code tenantId} (the tenant an
     * announcement was published in), whatever the caller's {@code tid}. A courier answering or
     * accepting an announcement of another tenant then reads it where it lives instead of getting
     * « Announcement not found ». {@code null} = no change.
     */
    public static <T> Mono<T> underTenant(UUID tenantId, Mono<T> work) {
        return tenantId == null ? work : work.contextWrite(ctx -> ctx.put(TENANT_OVERRIDE, tenantId));
    }

    /** Reactor-context key of {@link #underTenant}. */
    static final String TENANT_OVERRIDE = TenantContextHolder.class.getName() + ".override";

    private Mono<UUID> callerTenantId() {
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
