package com.yowyob.tiibntick.core.notify.adapter.in.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * A notification feed, and the preferences tied to it, belong to one user. The recipient id in the
 * URL is only honoured when it is the caller's own user/actor id; support agents and platform
 * administrators may read anyone's. Without this any authenticated client could list another user's
 * notifications (tracking codes, delivery status, payment results) by changing the path.
 */
final class RecipientAccessGuard {

    private static final Set<String> STAFF = Set.of("ROLE_SUPPORT_AGENT", "ROLE_TNT_ADMIN");

    private RecipientAccessGuard() {
    }

    /** Completes when allowed; errors with {@link AccessDeniedException} (403) otherwise. */
    static Mono<Void> check(TntUserIdentity caller, String recipientId) {
        if (recipientId != null && (isSame(caller.userId(), recipientId) || isSame(caller.actorId(), recipientId))) {
            return Mono.empty();
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication() != null
                        && ctx.getAuthentication().getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority).anyMatch(STAFF::contains))
                .defaultIfEmpty(false)
                .flatMap(staff -> staff ? Mono.<Void>empty()
                        : Mono.error(new AccessDeniedException("These notifications belong to another user.")));
    }

    private static boolean isSame(Object id, String recipientId) {
        return id != null && id.toString().equalsIgnoreCase(recipientId);
    }
}
