package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.common.tenant.SystemTenant;
import com.yowyob.tiibntick.core.roles.application.port.in.ManagePlatformAdminsUseCase;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformAdminAuthorities;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformGrantKind;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant;
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

/**
 * The real JWT converter and the platform administrators (decisions D-159 to D-175): TNT_ADMIN whatever the tenant
 * of the login, a single authority set for owner and granted admins, nothing privileged from a Kernel claim.
 */
class TntJwtPlatformAdminTest {

    static final String SYSTEM = SystemTenant.id().toString();
    static final String OTHER_TENANT = "22222222-2222-2222-2222-222222222222";
    static final UUID SUBJECT = UUID.fromString("0000000a-0000-0000-0000-0000000000aa");

    final ManagePlatformAdminsUseCase admins = mock(ManagePlatformAdminsUseCase.class);

    Set<String> authorities(ManagePlatformAdminsUseCase registry, String tid, Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject(SUBJECT.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).claim("tid", tid);
        claims.forEach(b::claim);
        TntSecurityConfig config = new TntSecurityConfig();
        ReflectionTestUtils.setField(config, "systemTenantId", SYSTEM);
        config.setPlatformAdmins(registry);
        return config.tntJwtAuthenticationConverter().convert(b.build()).block().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("D-162: forged TNT_ADMIN entries of the roles and permissions claims are dropped")
    void forgedClaimsAreDropped() {
        when(admins.isPlatformAdmin(SUBJECT)).thenReturn(Mono.just(false));
        Set<String> a = authorities(admins, OTHER_TENANT, Map.of(
                "roles", List.of("TNT_ADMIN", "ROLE_TNT_ADMIN", "AGENCY_MANAGER"),
                "permissions", List.of("ROLE_TNT_ADMIN", "ROLE_TNT_ADMIN#TENANT:" + OTHER_TENANT, "*",
                        "system:admin", "platform:clients", "TNT_SYSTEM_TENANT_OWNER", "ROLE_OWNER",
                        "ROLE_FREELANCER#TENANT:" + OTHER_TENANT, "parcel:read")));
        assertThat(a).doesNotContain("ROLE_TNT_ADMIN", "ROLE_TNT_ADMIN#TENANT:" + OTHER_TENANT, "*", "system:admin",
                "platform:clients", PlatformAdminAuthorities.SYSTEM_TENANT_OWNER_MARKER, "tnt:platform:admin");
        assertThat(a).contains("ROLE_FREELANCER", "parcel:read", "TENANT_" + OTHER_TENANT)
                .as("D-176: an agency role needs the Core's confirmation, not wired here").doesNotContain("ROLE_AGENCY_MANAGER");
    }

    @Test
    @DisplayName("D-161/D-167: the owner of the system tenant gets the admin set and its SYSTEM_OWNER grant, no business role")
    void ownerOfTheSystemTenant() {
        when(admins.ensureSystemOwner(SUBJECT)).thenReturn(Mono.just(PlatformRoleGrant.newGrant(SUBJECT,
                PlatformGrantKind.SYSTEM_OWNER, SUBJECT, UUID.fromString(SYSTEM), UUID.fromString(SYSTEM), Instant.now())));
        Set<String> a = authorities(admins, SYSTEM, Map.of("permissions", List.of("ROLE_OWNER", "tenant:admin")));
        assertThat(a).containsAll(PlatformAdminAuthorities.AUTHORITIES)
                .contains(PlatformAdminAuthorities.SYSTEM_TENANT_OWNER_MARKER)
                .doesNotContain("ROLE_AGENCY_MANAGER", "ROLE_CLIENT", "ROLE_FREELANCER", "ROLE_MEDIATOR");
        verify(admins).ensureSystemOwner(SUBJECT);
        verify(admins, never()).isPlatformAdmin(any());
    }

    @Test
    @DisplayName("the owner stays administrator when the registry cannot record its grant")
    void ownerWhenTheRegistryFails() {
        when(admins.ensureSystemOwner(SUBJECT)).thenReturn(Mono.error(new IllegalStateException("db down")));
        Set<String> a = authorities(admins, SYSTEM, Map.of("permissions", List.of("ROLE_OWNER")));
        assertThat(a).contains("ROLE_TNT_ADMIN", PlatformAdminAuthorities.SYSTEM_TENANT_OWNER_MARKER);
    }

    @Test
    @DisplayName("the owner of another tenant is not an administrator")
    void ownerOfAnotherTenant() {
        when(admins.isPlatformAdmin(SUBJECT)).thenReturn(Mono.just(false));
        Set<String> a = authorities(admins, OTHER_TENANT, Map.of("permissions", List.of("ROLE_OWNER", "tenant:admin")));
        assertThat(a).doesNotContain("ROLE_TNT_ADMIN", PlatformAdminAuthorities.SYSTEM_TENANT_OWNER_MARKER);
        verify(admins, never()).ensureSystemOwner(any());
    }

    @Test
    @DisplayName("D-159/D-160: a granted administrator logged in any tenant gets ROLE_TNT_ADMIN, so hasRole passes")
    void grantedAdministratorInAnotherTenant() {
        when(admins.isPlatformAdmin(SUBJECT)).thenReturn(Mono.just(true));
        Set<String> a = authorities(admins, OTHER_TENANT, Map.of("permissions", List.of("ROLE_CLIENT#TENANT:x")));
        assertThat(a).containsAll(PlatformAdminAuthorities.AUTHORITIES).contains("ROLE_CLIENT")
                .doesNotContain(PlatformAdminAuthorities.SYSTEM_TENANT_OWNER_MARKER);
    }

    @Test
    @DisplayName("fail closed: no administrator rights when the registry cannot answer")
    void registryDown() {
        when(admins.isPlatformAdmin(SUBJECT)).thenReturn(Mono.error(new IllegalStateException("db down")));
        assertThat(authorities(admins, OTHER_TENANT, Map.of())).doesNotContain("ROLE_TNT_ADMIN");
    }
}
