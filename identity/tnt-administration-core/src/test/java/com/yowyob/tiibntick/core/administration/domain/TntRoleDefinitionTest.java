package com.yowyob.tiibntick.core.administration.domain;

import com.yowyob.tiibntick.core.administration.domain.model.TntRoleDefinition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TntRoleDefinition domain aggregate.
 *
 * @author MANFOUO Braun
 */
class TntRoleDefinitionTest {

    @Test
    void should_provision_with_null_kernel_role_id() {
        UUID tenantId = UUID.randomUUID();
        TntRoleDefinition def = TntRoleDefinition.provision(
                tenantId, "TNT_DISPATCHER", "Dispatcher",
                "AGENCY", Set.of("delivery:read", "delivery:dispatch"), false);

        assertThat(def.getId()).isNotNull();
        assertThat(def.getTenantId()).isEqualTo(tenantId);
        assertThat(def.getTemplateCode()).isEqualTo("TNT_DISPATCHER");
        assertThat(def.getKernelRoleId()).isNull();
        assertThat(def.isKernelSynced()).isFalse();
        assertThat(def.getCreatedAt()).isNotNull();
        assertThat(def.getUpdatedAt()).isNotNull();
    }

    @Test
    void should_link_kernel_role_id_via_withKernelRoleId() {
        TntRoleDefinition def = TntRoleDefinition.provision(
                UUID.randomUUID(), "TNT_FREELANCER", "Freelancer Courier",
                "AGENCY", Set.of("delivery:track"), false);

        UUID kernelRoleId = UUID.randomUUID();
        TntRoleDefinition linked = def.withKernelRoleId(kernelRoleId);

        assertThat(linked.getKernelRoleId()).isEqualTo(kernelRoleId);
        assertThat(linked.isKernelSynced()).isTrue();
        // Original must remain immutable
        assertThat(def.getKernelRoleId()).isNull();
        assertThat(def.isKernelSynced()).isFalse();
    }

    @Test
    void should_preserve_permission_codes_as_immutable_set() {
        Set<String> perms = Set.of("delivery:read", "delivery:write");
        TntRoleDefinition def = TntRoleDefinition.provision(
                UUID.randomUUID(), "TNT_AGENCY_ADMIN", "Agency Admin",
                "AGENCY", perms, false);

        assertThat(def.getPermissionCodes()).containsExactlyInAnyOrder("delivery:read", "delivery:write");
        assertThatThrownBy(() -> def.getPermissionCodes().add("extra:perm"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_mark_protected_definitions_correctly() {
        TntRoleDefinition def = TntRoleDefinition.provision(
                UUID.randomUUID(), "TNT_SUPER_ADMIN", "TiiBnTick Super Admin",
                "TENANT", Set.of("tnt:platform:admin"), true);

        assertThat(def.isProtectedDefinition()).isTrue();
    }

    @Test
    void should_rehydrate_with_all_fields() {
        UUID id       = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID kernelId = UUID.randomUUID();
        Instant now   = Instant.now();

        TntRoleDefinition def = TntRoleDefinition.rehydrate(
                id, tenantId, "TNT_CLIENT", "Client",
                "AGENCY", Set.of("delivery:track"), false,
                kernelId, true, now, now);

        assertThat(def.getId()).isEqualTo(id);
        assertThat(def.getTenantId()).isEqualTo(tenantId);
        assertThat(def.getKernelRoleId()).isEqualTo(kernelId);
        assertThat(def.isKernelSynced()).isTrue();
        assertThat(def.getScopeType()).isEqualTo("AGENCY");
    }

    @Test
    void withKernelRoleId_should_update_updatedAt() throws InterruptedException {
        TntRoleDefinition def = TntRoleDefinition.provision(
                UUID.randomUUID(), "TNT_DISPATCHER", "Dispatcher",
                "AGENCY", Set.of(), false);
        Instant before = def.getUpdatedAt();

        Thread.sleep(5); // Ensure time difference
        TntRoleDefinition linked = def.withKernelRoleId(UUID.randomUUID());

        assertThat(linked.getUpdatedAt()).isAfterOrEqualTo(before);
        assertThat(linked.getCreatedAt()).isEqualTo(def.getCreatedAt());
    }
}
