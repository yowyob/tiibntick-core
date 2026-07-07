package com.yowyob.tiibntick.core.administration.domain;

import com.yowyob.tiibntick.core.administration.domain.model.TntPermissionCatalog;
import com.yowyob.tiibntick.core.administration.domain.model.TntPermissionEntry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TntPermissionEntry and TntPermissionCatalog.
 *
 * @author MANFOUO Braun
 */
class TntPermissionEntryTest {

    @Test
    void should_create_entry_without_kernel_id_by_default() {
        TntPermissionEntry entry = new TntPermissionEntry(
                "delivery:read", "Delivery Read", "Read deliveries",
                "DELIVERY", "AGENCY", false, true);

        assertThat(entry.code()).isEqualTo("delivery:read");
        assertThat(entry.kernelPermissionId()).isNull();
        assertThat(entry.system()).isFalse();
        assertThat(entry.assignable()).isTrue();
    }

    @Test
    void should_create_entry_with_kernel_id_via_full_constructor() {
        UUID kernelId = UUID.randomUUID();
        TntPermissionEntry entry = new TntPermissionEntry(
                "delivery:read", "Delivery Read", "Read deliveries",
                "DELIVERY", "AGENCY", false, true, kernelId);

        assertThat(entry.kernelPermissionId()).isEqualTo(kernelId);
    }

    @Test
    void should_return_copy_with_kernel_id_via_withKernelPermissionId() {
        TntPermissionEntry entry = new TntPermissionEntry(
                "delivery:write", "Delivery Write", "Write deliveries",
                "DELIVERY", "AGENCY", false, true);
        UUID kernelId = UUID.randomUUID();

        TntPermissionEntry updated = entry.withKernelPermissionId(kernelId);

        assertThat(updated.kernelPermissionId()).isEqualTo(kernelId);
        // Original must remain immutable
        assertThat(entry.kernelPermissionId()).isNull();
        assertThat(updated.code()).isEqualTo(entry.code());
    }

    @Test
    void catalog_should_have_expected_system_protected_permissions() {
        Map<String, TntPermissionEntry> catalog = TntPermissionCatalog.buildCatalog();

        assertThat(catalog).containsKey("tnt:platform:admin");
        assertThat(catalog).containsKey("tnt:blockchain:mine");
        assertThat(catalog).containsKey("tnt:blockchain:validate");

        assertThat(catalog.get("tnt:platform:admin").system()).isTrue();
        assertThat(catalog.get("tnt:platform:admin").assignable()).isFalse();
        // System-protected permissions have null kernelPermissionId — TNT-exclusive
        assertThat(catalog.get("tnt:blockchain:mine").kernelPermissionId()).isNull();
    }

    @Test
    void catalog_should_have_delivery_permissions_as_assignable() {
        Map<String, TntPermissionEntry> catalog = TntPermissionCatalog.buildCatalog();

        assertThat(catalog.get("delivery:read").system()).isFalse();
        assertThat(catalog.get("delivery:read").assignable()).isTrue();
        assertThat(catalog.get("delivery:admin").scope()).isEqualTo("ORGANIZATION");
    }

    @Test
    void catalog_should_contain_blockchain_permissions_with_correct_module() {
        Map<String, TntPermissionEntry> catalog = TntPermissionCatalog.buildCatalog();

        assertThat(catalog.get("blockchain:wallet:read").module()).isEqualTo("BLOCKCHAIN");
        assertThat(catalog.get("blockchain:anchor:create").module()).isEqualTo("BLOCKCHAIN");
    }

    @Test
    void catalog_should_be_immutable() {
        Map<String, TntPermissionEntry> catalog = TntPermissionCatalog.buildCatalog();
        assertThatThrownBy(() -> catalog.put("new:perm", null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void catalog_should_contain_administration_permissions() {
        Map<String, TntPermissionEntry> catalog = TntPermissionCatalog.buildCatalog();
        assertThat(catalog).containsKey("administration:roles:read");
        assertThat(catalog).containsKey("administration:settings:write");
        assertThat(catalog).containsKey("administration:govern:organizations");
    }
}
