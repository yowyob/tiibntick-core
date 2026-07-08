package com.yowyob.tiibntick.core.roles.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TntRoleTest {

    @ParameterizedTest
    @EnumSource(TntRole.class)
    void everyRole_shouldHaveNonBlankCode(TntRole role) {
        assertThat(role.code()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(TntRole.class)
    void everyRole_shouldHaveNonBlankLabel(TntRole role) {
        assertThat(role.label()).isNotBlank();
    }

    @ParameterizedTest
    @EnumSource(TntRole.class)
    void everyRole_shouldHaveScopeType(TntRole role) {
        assertThat(role.scopeType()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(TntRole.class)
    void everyRole_shouldHaveAtLeastOnePermission(TntRole role) {
        assertThat(role.defaultPermissions()).isNotEmpty();
    }

    @Test
    void tntAdmin_shouldHaveWildcardPermission() {
        assertThat(TntRole.TNT_ADMIN.defaultPermissions()).containsExactly(TntPermission.ALL);
        assertThat(TntRole.TNT_ADMIN.scopeType()).isEqualTo(RoleScopeType.SYSTEM);
    }

    @Test
    void agencyManager_shouldHaveAgencyScope() {
        assertThat(TntRole.AGENCY_MANAGER.scopeType()).isEqualTo(RoleScopeType.AGENCY);
    }

    @Test
    void freelancer_shouldHaveTenantScope() {
        assertThat(TntRole.FREELANCER.scopeType()).isEqualTo(RoleScopeType.TENANT);
    }

    @Test
    void orgAdmin_shouldHaveOrganizationScope() {
        assertThat(TntRole.ORG_ADMIN.scopeType()).isEqualTo(RoleScopeType.ORGANIZATION);
    }

    @Test
    void allRoleCodes_shouldBeUnique() {
        Set<String> codes = Arrays.stream(TntRole.values())
                .map(TntRole::code)
                .collect(Collectors.toSet());
        assertThat(codes).hasSize(TntRole.values().length);
    }

    @Test
    void fromCode_withValidCode_shouldReturnRole() {
        TntRole role = TntRole.fromCode("AGENCY_MANAGER");
        assertThat(role).isEqualTo(TntRole.AGENCY_MANAGER);
    }

    @Test
    void fromCode_caseInsensitive_shouldReturnRole() {
        assertThat(TntRole.fromCode("agency_manager")).isEqualTo(TntRole.AGENCY_MANAGER);
        assertThat(TntRole.fromCode("Freelancer")).isEqualTo(TntRole.FREELANCER);
    }

    @Test
    void fromCode_withUnknownCode_shouldThrow() {
        assertThatThrownBy(() -> TntRole.fromCode("UNKNOWN_ROLE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN_ROLE");
    }

    @Test
    void isKnownRole_withValidCode_shouldReturnTrue() {
        assertThat(TntRole.isKnownRole("PERMANENT_DELIVERER")).isTrue();
        assertThat(TntRole.isKnownRole("CLIENT")).isTrue();
    }

    @Test
    void isKnownRole_withUnknownCode_shouldReturnFalse() {
        assertThat(TntRole.isKnownRole("NOT_A_ROLE")).isFalse();
        assertThat(TntRole.isKnownRole(null)).isFalse();
        assertThat(TntRole.isKnownRole("")).isFalse();
    }

    @Test
    void agencyManager_shouldIncludeMissionAndBillingPermissions() {
        Set<String> permissions = TntRole.AGENCY_MANAGER.defaultPermissions();
        assertThat(permissions).contains(
                TntPermission.MISSION_CREATE,
                TntPermission.MISSION_ASSIGN,
                TntPermission.BILLING_READ,
                TntPermission.BILLING_WRITE,
                TntPermission.REPORT_EXPORT
        );
    }

    @Test
    void permanentDeliverer_shouldNotHaveAdminOrBillingWrite() {
        Set<String> permissions = TntRole.PERMANENT_DELIVERER.defaultPermissions();
        assertThat(permissions).doesNotContain(
                TntPermission.BILLING_WRITE,
                TntPermission.ADMIN_ROLES,
                TntPermission.AGENCY_MANAGE
        );
    }

    @Test
    void client_shouldHaveAnnouncementAndPaymentPermissions() {
        Set<String> permissions = TntRole.CLIENT.defaultPermissions();
        assertThat(permissions).contains(
                TntPermission.ANNOUNCEMENT_CREATE,
                TntPermission.ANNOUNCEMENT_ELECT,
                TntPermission.PAYMENT_PROCESS,
                TntPermission.TRUST_VERIFY
        );
    }

    @Test
    void tntRoleDefinition_fromRole_shouldMapAllFields() {
        TntRoleDefinition def = TntRoleDefinition.from(TntRole.RELAY_OPERATOR);

        assertThat(def.code()).isEqualTo("RELAY_OPERATOR");
        assertThat(def.name()).isEqualTo("Relay Point Operator");
        assertThat(def.scopeType()).isEqualTo(RoleScopeType.AGENCY);
        assertThat(def.systemRole()).isTrue();
        assertThat(def.defaultPermissions()).contains(
                TntPermission.RELAY_OPERATE,
                TntPermission.TRUST_ANCHOR,
                TntPermission.DELIVERY_CONFIRM
        );
    }

    @Test
    void tntRoleDefinition_scopeTypeCode_shouldReturnKernelCompatibleString() {
        TntRoleDefinition agencyDef = TntRoleDefinition.from(TntRole.AGENCY_MANAGER);
        assertThat(agencyDef.scopeTypeCode()).isEqualTo("AGENCY");

        TntRoleDefinition systemDef = TntRoleDefinition.from(TntRole.TNT_ADMIN);
        assertThat(systemDef.scopeTypeCode()).isEqualTo("SYSTEM");
    }

    @Test
    void tntPermissionContext_withUserId_andTenantId_shouldBeValid() {
        var ctx = TntPermissionContext.of(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID()
        );
        assertThat(ctx.userId()).isNotNull();
        assertThat(ctx.tenantId()).isNotNull();
        assertThat(ctx.agencyId()).isNull();
        assertThat(ctx.hasAgencyScope()).isFalse();
    }

    @Test
    void tntPermissionContext_withAgencyId_shouldDetectAgencyScope() {
        var ctx = TntPermissionContext.full(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID()
        );
        assertThat(ctx.hasAgencyScope()).isTrue();
    }
}
