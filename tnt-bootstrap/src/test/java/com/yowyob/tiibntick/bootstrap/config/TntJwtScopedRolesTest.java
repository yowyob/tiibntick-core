package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.common.tenant.SystemTenant;
import com.yowyob.tiibntick.core.roles.application.port.in.ConfirmScopedRolesUseCase;
import com.yowyob.tiibntick.core.roles.application.port.in.ManagePlatformAdminsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Decision D-176 on the real converter: agency/organisation roles of a token need the Core's confirmation. */
class TntJwtScopedRolesTest {

    static final UUID TENANT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID SUBJECT = UUID.fromString("0000000b-0000-0000-0000-0000000000bb");
    static final String AGENCY = "aaaaaaaa-0000-0000-0000-000000000001";

    final ConfirmScopedRolesUseCase scoped = mock(ConfirmScopedRolesUseCase.class);

    Set<String> authorities(ConfirmScopedRolesUseCase confirmation, Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject(SUBJECT.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claim("tid", TENANT.toString());
        claims.forEach(b::claim);
        TntSecurityConfig config = new TntSecurityConfig();
        ReflectionTestUtils.setField(config, "systemTenantId", SystemTenant.id().toString());
        ManagePlatformAdminsUseCase admins = mock(ManagePlatformAdminsUseCase.class);
        when(admins.isPlatformAdmin(any())).thenReturn(Mono.just(false));
        config.setPlatformAdmins(admins);
        config.setScopedRoles(confirmation);
        return config.tntJwtAuthenticationConverter().convert(b.build()).block().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("a forged agency role (no Core assignment) is dropped from both claims")
    void forgedAgencyRolesAreDropped() {
        when(scoped.confirmedScopedRoles(TENANT, SUBJECT)).thenReturn(Mono.just(Set.of()));
        Set<String> a = authorities(scoped, Map.of("roles", List.of("AGENCY_MANAGER", "CLIENT"),
                "permissions", List.of("ROLE_ORG_ADMIN#ORGANIZATION:" + AGENCY, "ROLE_AGENCY_ADMIN", "parcel:read")));
        assertThat(a).doesNotContain("ROLE_AGENCY_MANAGER", "ROLE_ORG_ADMIN#ORGANIZATION:" + AGENCY)
                .contains("ROLE_CLIENT", "ROLE_AGENCY_ADMIN", "parcel:read", "TENANT_" + TENANT);
    }

    @Test
    @DisplayName("a role the Core assigned is kept, on its own scope only")
    void confirmedRolesAreKeptOnTheirScope() {
        when(scoped.confirmedScopedRoles(TENANT, SUBJECT))
                .thenReturn(Mono.just(Set.of("AGENCY_MANAGER", "AGENCY_MANAGER#AGENCY:" + AGENCY)));
        String otherAgency = "ROLE_AGENCY_MANAGER#AGENCY:" + UUID.randomUUID();
        Set<String> a = authorities(scoped, Map.of("roles", List.of("AGENCY_MANAGER"),
                "permissions", List.of("ROLE_AGENCY_MANAGER#AGENCY:" + AGENCY, otherAgency, "ROLE_BRANCH_MANAGER")));
        assertThat(a).contains("ROLE_AGENCY_MANAGER", "ROLE_AGENCY_MANAGER#AGENCY:" + AGENCY)
                .doesNotContain(otherAgency, "ROLE_BRANCH_MANAGER");
    }

    @Test
    @DisplayName("fail closed: no agency role when the assignments cannot be read")
    void confirmationDown() {
        when(scoped.confirmedScopedRoles(TENANT, SUBJECT)).thenReturn(Mono.error(new IllegalStateException("db")));
        assertThat(authorities(scoped, Map.of("roles", List.of("AGENCY_MANAGER", "FREELANCER"))))
                .doesNotContain("ROLE_AGENCY_MANAGER").contains("ROLE_FREELANCER");
    }

    @Test
    @DisplayName("no lookup at all for a token without agency/organisation role")
    void noLookupWhenNotNeeded() {
        authorities(scoped, Map.of("roles", List.of("CLIENT"), "permissions", List.of("ROLE_FREELANCER#TENANT:x")));
        verify(scoped, never()).confirmedScopedRoles(any(), any());
    }

    @Test
    @DisplayName("without the confirmation service (slice context), agency roles of the token are dropped")
    void notWired() {
        assertThat(authorities(null, Map.of("roles", List.of("AGENCY_MANAGER")))).doesNotContain("ROLE_AGENCY_MANAGER");
    }
}
