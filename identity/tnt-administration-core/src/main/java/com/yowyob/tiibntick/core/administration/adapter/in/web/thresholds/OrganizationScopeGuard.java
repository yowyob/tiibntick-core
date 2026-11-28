package com.yowyob.tiibntick.core.administration.adapter.in.web.thresholds;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Restricts the organization routes to the caller's own organization: the permission
 * ({@code organization:delivery-settings:*}, checked by {@code @RequirePermission}) says what the caller may do, this
 * guard says on which organization. A TiiBnTick platform administrator ({@code tnt:platform:admin}) may act on any
 * organization.
 */
@Component
public class OrganizationScopeGuard {

    static final String PLATFORM_ADMIN_RESOURCE = "tnt:platform";
    static final String PLATFORM_ADMIN_ACTION = "admin";

    private final TntPermissionEvaluator permissions;

    public OrganizationScopeGuard(TntPermissionEvaluator permissions) {
        this.permissions = permissions;
    }

    /** Completes when the caller belongs to {@code organizationId} or is a platform administrator; else 403. */
    public Mono<Void> check(TntUserIdentity caller, UUID organizationId) {
        if (caller != null && organizationId.equals(caller.organizationId())) {
            return Mono.empty();
        }
        return permissions.canFromCurrentContext(PLATFORM_ADMIN_RESOURCE, PLATFORM_ADMIN_ACTION)
                .flatMap(admin -> admin ? Mono.<Void>empty() : Mono.error(new TntRoleException("ROLE_FORBIDDEN",
                        "Access denied: organization " + organizationId + " is not the caller's organization")));
    }
}
