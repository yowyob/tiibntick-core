package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.application.port.in.ManagePlatformAdminsUseCase.Caller;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformAdminAuthorities;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reads the acting {@link Caller} from the reactive security context populated by the JWT converter: the subject
 * (Kernel user id), the synthetic {@code TENANT_<tid>} authority and the owner marker the Core adds itself.
 *
 * @author MANFOUO Braun
 */
public final class PlatformAdminCallers {

    private PlatformAdminCallers() {
    }

    public static Mono<Caller> current() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(PlatformAdminCallers::of);
    }

    public static Caller of(Authentication auth) {
        UUID tenant = null;
        boolean owner = false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            String s = a.getAuthority();
            if (s == null) continue;
            if (s.equals(PlatformAdminAuthorities.SYSTEM_TENANT_OWNER_MARKER)) {
                owner = true;
            } else if (tenant == null && s.startsWith("TENANT_")) {
                tenant = uuid(s.substring("TENANT_".length()));
            }
        }
        return new Caller(uuid(auth.getName()), tenant, owner);
    }

    private static UUID uuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
