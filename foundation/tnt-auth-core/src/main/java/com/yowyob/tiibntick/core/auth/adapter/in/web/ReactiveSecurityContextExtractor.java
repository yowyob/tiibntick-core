package com.yowyob.tiibntick.core.auth.adapter.in.web;

import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.exception.TntAuthException;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive helper for extracting TiiBnTick security context from any point
 * in the reactive pipeline — services, event handlers, domain logic.
 *
 * <p>This is the primary programmatic access point for non-controller code.
 * Application services use this instead of calling {@code ReactiveSecurityContextHolder}
 * directly, preserving the hexagonal boundary from the Kernel's types.
 *
 * <p>Example usage in a service:
 * <pre>{@code
 * return extractor.requireUserId()
 *     .flatMap(userId -> missionRepository.findByDeliverer(userId, tenantId));
 * }</pre>
 *
 * @author MANFOUO Braun
 */
public class ReactiveSecurityContextExtractor {

    private final ResolveCurrentUserUseCase resolveCurrentUserUseCase;

    public ReactiveSecurityContextExtractor(ResolveCurrentUserUseCase resolveCurrentUserUseCase) {
        this.resolveCurrentUserUseCase = resolveCurrentUserUseCase;
    }

    /**
     * Returns the full TntSecurityContext or anonymous if not authenticated.
     */
    public Mono<TntSecurityContext> extract() {
        return resolveCurrentUserUseCase.resolveCurrentContextOrAnonymous();
    }

    /**
     * Returns the full TntSecurityContext and fails if not authenticated.
     */
    public Mono<TntSecurityContext> requireAuthenticated() {
        return resolveCurrentUserUseCase.resolveCurrentContext();
    }

    /**
     * Returns the current userId or fails with {@link TntAuthException#missingContext()}.
     */
    public Mono<UUID> requireUserId() {
        return resolveCurrentUserUseCase.resolveCurrentContext()
                .map(TntSecurityContext::userId);
    }

    /**
     * Returns the current tenantId or fails with {@link TntAuthException#missingContext()}.
     */
    public Mono<UUID> requireTenantId() {
        return resolveCurrentUserUseCase.resolveCurrentContext()
                .map(TntSecurityContext::tenantId);
    }

    /**
     * Returns the current actorId or fails if not linked to a TiiBnTick actor profile.
     */
    public Mono<UUID> requireActorId() {
        return resolveCurrentUserUseCase.resolveCurrentContext()
                .flatMap(ctx -> {
                    if (ctx.actorId() == null) {
                        return Mono.error(TntAuthException.actorNotLinked(
                                ctx.userId() != null ? ctx.userId().toString() : "unknown"));
                    }
                    return Mono.just(ctx.actorId());
                });
    }

    /**
     * Returns the lightweight identity projection or empty if not authenticated.
     */
    public Mono<TntUserIdentity> resolveIdentity() {
        return resolveCurrentUserUseCase.resolveCurrentContextOrAnonymous()
                .filter(TntSecurityContext::isFullyAuthenticated)
                .map(TntUserIdentity::from);
    }

    /**
     * Asserts the current user has the given permission, failing reactively if not.
     *
     * @param resource permission resource (e.g. "mission")
     * @param action   permission action (e.g. "create")
     */
    public Mono<TntSecurityContext> requirePermission(String resource, String action) {
        return resolveCurrentUserUseCase.resolveCurrentContext()
                .flatMap(ctx -> {
                    if (!ctx.hasPermission(resource, action)) {
                        return Mono.error(TntAuthException.forbidden(resource, action));
                    }
                    return Mono.just(ctx);
                });
    }
}
