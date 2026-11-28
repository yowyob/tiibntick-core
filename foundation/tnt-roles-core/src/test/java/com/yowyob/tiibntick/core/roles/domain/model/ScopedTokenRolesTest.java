package com.yowyob.tiibntick.core.roles.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Decision D-176: which token entries need the Core's confirmation, and in which canonical form. */
class ScopedTokenRolesTest {

    static final UUID AGENCY = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Test
    void agencyAndOrganisationRolesAreConcerned() {
        assertThat(ScopedTokenRoles.entryOf("AGENCY_MANAGER")).contains("AGENCY_MANAGER");
        assertThat(ScopedTokenRoles.entryOf("ROLE_branch_manager")).contains("BRANCH_MANAGER");
        assertThat(ScopedTokenRoles.entryOf("ROLE_AGENCY_MANAGER#TENANT:x")).contains("AGENCY_MANAGER");
        assertThat(ScopedTokenRoles.entryOf("ROLE_AGENCY_MANAGER#AGENCY:" + AGENCY.toString().toUpperCase()))
                .contains("AGENCY_MANAGER#AGENCY:" + AGENCY);
        assertThat(ScopedTokenRoles.entryOf("ROLE_ORG_ADMIN#ORG:" + AGENCY)).contains("ORG_ADMIN#ORGANIZATION:" + AGENCY);
        assertThat(ScopedTokenRoles.entryOf("ROLE_RELAY_OPERATOR")).contains("RELAY_OPERATOR");
    }

    @Test
    void everythingElseIsLeftAlone() {
        assertThat(ScopedTokenRoles.entryOf("ROLE_CLIENT")).isEmpty();
        assertThat(ScopedTokenRoles.entryOf("ROLE_FREELANCER#TENANT:x")).isEmpty();
        assertThat(ScopedTokenRoles.entryOf("ROLE_SUPPORT_AGENT")).isEmpty();
        assertThat(ScopedTokenRoles.entryOf("ROLE_AGENCY_ADMIN")).as("Kernel-only code").isEmpty();
        assertThat(ScopedTokenRoles.entryOf("mission:create#AGENCY:" + AGENCY)).isEmpty();
        assertThat(ScopedTokenRoles.entryOf("TENANT_" + AGENCY)).isEmpty();
        assertThat(ScopedTokenRoles.entryOf(null)).isEmpty();
    }

    @Test
    void anAssignmentProvesTheBareCodeAndItsOwnScopeOnly() {
        Set<String> proven = ScopedTokenRoles.provenBy("agency_manager", RoleScopeType.AGENCY, AGENCY);
        assertThat(ScopedTokenRoles.confirmed("AGENCY_MANAGER", proven)).isTrue();
        assertThat(ScopedTokenRoles.confirmed("ROLE_AGENCY_MANAGER#AGENCY:" + AGENCY, proven)).isTrue();
        assertThat(ScopedTokenRoles.confirmed("ROLE_AGENCY_MANAGER#AGENCY:" + UUID.randomUUID(), proven)).isFalse();
        assertThat(ScopedTokenRoles.confirmed("ROLE_BRANCH_MANAGER", proven)).isFalse();
        assertThat(ScopedTokenRoles.confirmed("ROLE_CLIENT", Set.of())).as("not concerned").isTrue();
    }
}
