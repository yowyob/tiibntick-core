package com.yowyob.tiibntick.core.auth.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TntSecurityContextTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID AGENCY_ID = UUID.randomUUID();

    @Test
    void anonymous_shouldReturnUnauthenticatedContext() {
        TntSecurityContext ctx = TntSecurityContext.anonymous();

        assertThat(ctx.authenticated()).isFalse();
        assertThat(ctx.userId()).isNull();
        assertThat(ctx.tenantId()).isNull();
        assertThat(ctx.actorId()).isNull();
        assertThat(ctx.roles()).isEmpty();
        assertThat(ctx.permissions()).isEmpty();
        assertThat(ctx.isFullyAuthenticated()).isFalse();
    }

    @Test
    void hasPermission_exactMatch_shouldReturnTrue() {
        TntSecurityContext ctx = buildFullContext(Set.of("mission:create", "report:read"));

        assertThat(ctx.hasPermission("mission", "create")).isTrue();
        assertThat(ctx.hasPermission("report", "read")).isTrue();
    }

    @Test
    void hasPermission_wildcardAction_shouldGrantAnyAction() {
        TntSecurityContext ctx = buildFullContext(Set.of("mission:*"));

        assertThat(ctx.hasPermission("mission", "create")).isTrue();
        assertThat(ctx.hasPermission("mission", "delete")).isTrue();
        assertThat(ctx.hasPermission("report", "read")).isFalse();
    }

    @Test
    void hasPermission_globalWildcard_shouldGrantEverything() {
        TntSecurityContext ctx = buildFullContext(Set.of("*"));

        assertThat(ctx.hasPermission("mission", "create")).isTrue();
        assertThat(ctx.hasPermission("anything", "read")).isTrue();
    }

    @Test
    void hasPermission_notGranted_shouldReturnFalse() {
        TntSecurityContext ctx = buildFullContext(Set.of("mission:read"));

        assertThat(ctx.hasPermission("mission", "delete")).isFalse();
    }

    @Test
    void hasRole_withRolePrefix_shouldMatch() {
        TntSecurityContext ctx = buildContextWithRoles(Set.of("ROLE_AGENCY_MANAGER", "ROLE_DELIVERER"));

        assertThat(ctx.hasRole("ROLE_AGENCY_MANAGER")).isTrue();
        assertThat(ctx.hasRole("AGENCY_MANAGER")).isTrue();
        assertThat(ctx.hasRole("ROLE_CLIENT")).isFalse();
    }

    @Test
    void isFullyAuthenticated_withAllRequired_shouldReturnTrue() {
        TntSecurityContext ctx = buildFullContext(Set.of());

        assertThat(ctx.isFullyAuthenticated()).isTrue();
    }

    @Test
    void hasActorProfile_withActorId_shouldReturnTrue() {
        TntSecurityContext ctx = buildFullContext(Set.of());

        assertThat(ctx.hasActorProfile()).isTrue();
    }

    @Test
    void hasActorProfile_withoutActorId_shouldReturnFalse() {
        TntSecurityContext ctx = TntSecurityContext.builder()
                .userId(USER_ID)
                .tenantId(TENANT_ID)
                .authenticated(true)
                .build();

        assertThat(ctx.hasActorProfile()).isFalse();
    }

    @Test
    void builder_shouldConstructEquivalentContext() {
        TntSecurityContext ctx = TntSecurityContext.builder()
                .userId(USER_ID)
                .tenantId(TENANT_ID)
                .actorId(ACTOR_ID)
                .agencyId(AGENCY_ID)
                .permissions(Set.of("mission:create"))
                .roles(Set.of("ROLE_AGENCY_MANAGER"))
                .authenticated(true)
                .freelancer(false)
                .clientApplicationId("client-app-1")
                .build();

        assertThat(ctx.userId()).isEqualTo(USER_ID);
        assertThat(ctx.tenantId()).isEqualTo(TENANT_ID);
        assertThat(ctx.actorId()).isEqualTo(ACTOR_ID);
        assertThat(ctx.agencyId()).isEqualTo(AGENCY_ID);
        assertThat(ctx.authenticated()).isTrue();
        assertThat(ctx.permissions()).containsExactly("mission:create");
        assertThat(ctx.roles()).containsExactly("ROLE_AGENCY_MANAGER");
    }

    @Test
    void permissions_shouldBeImmutableAfterConstruction() {
        TntSecurityContext ctx = buildFullContext(Set.of("mission:read"));

        assertThat(ctx.permissions())
                .isUnmodifiable()
                .containsExactly("mission:read");
    }

    private TntSecurityContext buildFullContext(Set<String> permissions) {
        return TntSecurityContext.builder()
                .userId(USER_ID)
                .tenantId(TENANT_ID)
                .actorId(ACTOR_ID)
                .agencyId(AGENCY_ID)
                .permissions(permissions)
                .authenticated(true)
                .build();
    }

    private TntSecurityContext buildContextWithRoles(Set<String> roles) {
        return TntSecurityContext.builder()
                .userId(USER_ID)
                .tenantId(TENANT_ID)
                .actorId(ACTOR_ID)
                .roles(roles)
                .authenticated(true)
                .build();
    }
}
