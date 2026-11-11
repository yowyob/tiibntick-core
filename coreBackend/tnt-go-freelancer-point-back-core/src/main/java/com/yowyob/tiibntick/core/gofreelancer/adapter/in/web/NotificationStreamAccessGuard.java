package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.application.port.in.ResolveCurrentUserUseCase;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * A freelancer's notification stream carries matching offers and delivery news: only that
 * freelancer (profile id, or the caller's own user id) may subscribe. Platform administrators
 * ({@code *} permission) may watch any stream.
 */
@Component
@RequiredArgsConstructor
public class NotificationStreamAccessGuard {

    private final ResolveCurrentUserUseCase currentUser;
    private final GofpFreelancerRepository freelancers;

    /** Completes when allowed, errors with {@link AccessDeniedException} otherwise. */
    public Mono<Void> assertOwner(UUID streamOwnerId) {
        return currentUser.resolveCurrentContextOrAnonymous()
                .defaultIfEmpty(TntSecurityContext.anonymous())
                .flatMap(ctx -> {
                    if (!ctx.isFullyAuthenticated()) {
                        return Mono.<Void>error(new AccessDeniedException("Authentication required"));
                    }
                    if (ctx.permissions().contains("*") || streamOwnerId.equals(ctx.userId())) {
                        return Mono.<Void>empty();
                    }
                    return freelancers.findByCoreUserId(ctx.userId())
                            .map(profile -> streamOwnerId.equals(profile.getId()))
                            .defaultIfEmpty(false)
                            .flatMap(owner -> owner ? Mono.<Void>empty()
                                    : Mono.<Void>error(new AccessDeniedException(
                                            "This notification stream belongs to another freelancer")));
                });
    }
}
