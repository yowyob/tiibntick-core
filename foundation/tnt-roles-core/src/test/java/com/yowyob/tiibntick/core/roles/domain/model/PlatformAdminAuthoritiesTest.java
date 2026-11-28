package com.yowyob.tiibntick.core.roles.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Decision D-162: what a Kernel token can never bring by itself. */
class PlatformAdminAuthoritiesTest {

    @Test
    void entriesThatWouldMakeAnAdministratorAreForgeable() {
        for (String forged : new String[]{"ROLE_TNT_ADMIN", "ROLE_TNT_ADMIN#TENANT:x", "tnt_admin", "TNT_ADMIN",
                "ROLE_TNT_ADMIN#SYSTEM", "*", "*#TENANT:x", "system:admin", "system:*", "system:admin#TENANT:x",
                "platform:clients", "tnt:platform:admin", "TNT_SYSTEM_TENANT_OWNER"}) {
            assertThat(PlatformAdminAuthorities.isForgeableAdminClaim(forged)).as(forged).isTrue();
        }
    }

    @Test
    void everyPermissionOfANonAdministratorRoleStaysUsable() {
        for (TntRole role : TntRole.values()) {
            if (role == TntRole.TNT_ADMIN) continue;
            assertThat(PlatformAdminAuthorities.isForgeableAdminClaim("ROLE_" + role.code())).as(role.code()).isFalse();
            assertThat(PlatformAdminAuthorities.isForgeableAdminClaim("ROLE_" + role.code() + "#TENANT:x")).isFalse();
            for (String p : role.defaultPermissions()) {
                assertThat(PlatformAdminAuthorities.isForgeableAdminClaim(p)).as(role.code() + " " + p).isFalse();
            }
        }
        assertThat(PlatformAdminAuthorities.isForgeableAdminClaim("ROLE_OWNER")).isFalse();
        assertThat(PlatformAdminAuthorities.isForgeableAdminClaim(null)).isFalse();
    }

    @Test
    void theAdminOnlyPermissionsAreTheOnesNoOtherRoleHolds() {
        assertThat(PlatformAdminAuthorities.adminOnly())
                .contains(TntPermission.SYSTEM_ADMIN, TntPermission.PLATFORM_CLIENTS_MANAGE,
                        TntPermission.TNT_PLATFORM_ADMIN)
                .doesNotContain(TntPermission.ALL);
        System.out.println("admin-only permissions: " + new java.util.TreeSet<>(PlatformAdminAuthorities.adminOnly()));
    }

    @Test
    void theAdministratorSetHasNoBusinessRole() {
        assertThat(PlatformAdminAuthorities.AUTHORITIES).contains("ROLE_TNT_ADMIN", "tnt:platform:admin")
                .noneMatch(a -> a.startsWith("ROLE_") && !a.equals("ROLE_TNT_ADMIN"));
    }
}
