package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import yowyob.comops.api.kernel.application.port.in.CurrentTenantUseCase;
import yowyob.comops.api.kernel.domain.model.TenantContext;

@Component
public class TntCurrentTenantUseCase implements CurrentTenantUseCase {

    @Override
    public Mono<TenantContext> currentTenant() {
        return ReactiveSecurityContextHolder.getContext()
            .flatMap(ctx -> {
                var auth = ctx.getAuthentication();
                if (auth == null || !auth.isAuthenticated()) {
                    return Mono.empty();
                }

                //Your TntSecurityContext is injected as the principal by the TiiBnTick authentication filter
                if (auth.getPrincipal() instanceof TntSecurityContext tntCtx) {
                    return Mono.just(new TenantContext(
                        tntCtx.tenantId(),
                        tntCtx.organizationId(),
                        tntCtx.agencyId(),
                        tntCtx.userId(),
                        tntCtx.actorId()
                    ));
                }

                return Mono.empty();
            })
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No TntSecurityContext available — tenant cannot be resolved")));
    }
}


/*

@Override
public Mono<TenantContext> currentTenant() {
    return ReactiveSecurityContextHolder.getContext()
        .flatMap(ctx -> {
            var auth = ctx.getAuthentication();
            if (auth == null) return Mono.empty();

            // Essayer principal d'abord
            if (auth.getPrincipal() instanceof TntSecurityContext tntCtx) {
                return toTenantContext(tntCtx);
            }

            // Fallback sur details
            if (auth.getDetails() instanceof TntSecurityContext tntCtx) {
                return toTenantContext(tntCtx);
            }

            return Mono.empty();
        })
        .switchIfEmpty(Mono.error(
            new IllegalStateException("No TntSecurityContext available")));
}

private Mono<TenantContext> toTenantContext(TntSecurityContext ctx) {
    if (ctx.tenantId() == null) {
        return Mono.error(new IllegalStateException("tenantId is required"));
    }
    return Mono.just(new TenantContext(
        ctx.tenantId(),
        ctx.organizationId(),
        ctx.agencyId(),
        ctx.userId(),
        ctx.actorId()
    ));
}

*/