package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import com.yowyob.tiibntick.core.roles.domain.model.TntRoleDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TntRoleDefinitionRegistryTest {

    private TntRoleDefinitionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new TntRoleDefinitionRegistry();
    }

    @Test
    void getAllDefinitions_shouldReturnAllTntRoles() {
        List<TntRoleDefinition> all = registry.getAllDefinitions();

        assertThat(all).hasSize(TntRole.values().length);
        assertThat(all).extracting(TntRoleDefinition::code)
                .containsExactlyInAnyOrder(
                        "AGENCY_MANAGER", "BRANCH_MANAGER", "PERMANENT_DELIVERER",
                        "FREELANCER", "RELAY_OPERATOR", "CLIENT",
                        "SUPPORT_AGENT", "ORG_ADMIN", "TNT_ADMIN"
                );
    }

    @Test
    void findByCode_withValidCode_shouldReturnDefinition() {
        Optional<TntRoleDefinition> result = registry.findByCode("AGENCY_MANAGER");
        assertThat(result).isPresent();
        assertThat(result.get().code()).isEqualTo("AGENCY_MANAGER");
    }

    @Test
    void findByCode_caseInsensitive_shouldWork() {
        assertThat(registry.findByCode("agency_manager")).isPresent();
        assertThat(registry.findByCode("Agency_Manager")).isPresent();
    }

    @Test
    void findByCode_withUnknownCode_shouldReturnEmpty() {
        assertThat(registry.findByCode("NOT_A_ROLE")).isEmpty();
        assertThat(registry.findByCode(null)).isEmpty();
        assertThat(registry.findByCode("")).isEmpty();
    }

    @Test
    void getByCode_withUnknownCode_shouldThrowTntRoleException() {
        assertThatThrownBy(() -> registry.getByCode("FAKE_ROLE"))
                .isInstanceOf(TntRoleException.class)
                .hasMessageContaining("FAKE_ROLE");
    }

    @Test
    void getSystemRoles_shouldOnlyReturnSystemFlaggedRoles() {
        List<TntRoleDefinition> systemRoles = registry.getSystemRoles();

        assertThat(systemRoles).isNotEmpty();
        assertThat(systemRoles).allMatch(TntRoleDefinition::systemRole);
    }

    @Test
    void isKnownRole_withKnownCode_shouldReturnTrue() {
        assertThat(registry.isKnownRole("FREELANCER")).isTrue();
        assertThat(registry.isKnownRole("TNT_ADMIN")).isTrue();
    }

    @Test
    void isKnownRole_withUnknownCode_shouldReturnFalse() {
        assertThat(registry.isKnownRole("DRIVER")).isFalse();
        assertThat(registry.isKnownRole(null)).isFalse();
    }

    @Test
    void getRoleHierarchy_shouldStartWithTntAdmin() {
        List<TntRole> hierarchy = registry.getRoleHierarchy();
        assertThat(hierarchy.get(0)).isEqualTo(TntRole.TNT_ADMIN);
    }

    @Test
    void getRoleHierarchy_shouldEndWithClient() {
        List<TntRole> hierarchy = registry.getRoleHierarchy();
        assertThat(hierarchy.get(hierarchy.size() - 1)).isEqualTo(TntRole.CLIENT);
    }

    @Test
    void hierarchyIndex_agencyManagerIsHigherThanClient() {
        int agencyManagerIdx = registry.hierarchyIndex("AGENCY_MANAGER");
        int clientIdx = registry.hierarchyIndex("CLIENT");
        assertThat(agencyManagerIdx).isLessThan(clientIdx);
    }

    @Test
    void hierarchyIndex_tntAdminIsHighestPrivilege() {
        int tntAdminIdx = registry.hierarchyIndex("TNT_ADMIN");
        for (TntRole role : TntRole.values()) {
            if (role != TntRole.TNT_ADMIN) {
                assertThat(tntAdminIdx).isLessThanOrEqualTo(registry.hierarchyIndex(role.code()));
            }
        }
    }

    @Test
    void hierarchyIndex_unknownRole_shouldReturnMaxValue() {
        assertThat(registry.hierarchyIndex("GHOST_ROLE")).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void size_shouldMatchTntRoleEnumSize() {
        assertThat(registry.size()).isEqualTo(TntRole.values().length);
    }
}
