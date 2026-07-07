package com.yowyob.tiibntick.core.organization.domain;

import com.yowyob.tiibntick.core.organization.domain.model.Agency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link Agency} domain aggregate.
 *
 * <p>Tests cover: factory creation, Kernel integration key enforcement,
 * business method behaviour, and defensive validation.
 *
 * @author MANFOUO Braun
 */
@DisplayName("Agency — Domain aggregate tests")
class AgencyTest {

    private static final UUID KERNEL_ORG_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    @DisplayName("create() should produce an Agency with generated ID and XAF default currency")
    void create_shouldProduceAgencyWithDefaultCurrency() {
        // When
        Agency agency = Agency.create(KERNEL_ORG_ID, TENANT_ID, "TiiBnTick Douala", "RC/DLA/2024/001", null);

        // Then
        assertThat(agency.getId()).isNotNull();
        assertThat(agency.getId().value()).isNotNull();
        assertThat(agency.getOrganizationId()).isEqualTo(KERNEL_ORG_ID);
        assertThat(agency.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(agency.getName()).isEqualTo("TiiBnTick Douala");
        assertThat(agency.getPrimaryCurrency()).isEqualTo("XAF");  // default when null
        assertThat(agency.getCreatedAt()).isNotNull();
        assertThat(agency.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("create() should accept an explicit currency")
    void create_shouldAcceptExplicitCurrency() {
        // When
        Agency agency = Agency.create(KERNEL_ORG_ID, TENANT_ID, "TiiBnTick Lagos", "RC/LOS/2024/999", "NGN");

        // Then
        assertThat(agency.getPrimaryCurrency()).isEqualTo("NGN");
    }

    @Test
    @DisplayName("constructor should throw when organizationId is null")
    void constructor_shouldThrowWhenOrganizationIdIsNull() {
        assertThatThrownBy(() ->
                Agency.create(null, TENANT_ID, "Broken Agency", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("organizationId");
    }

    @Test
    @DisplayName("constructor should throw when tenantId is null")
    void constructor_shouldThrowWhenTenantIdIsNull() {
        assertThatThrownBy(() ->
                Agency.create(KERNEL_ORG_ID, null, "Broken Agency", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    @DisplayName("updateIdentity() should update name and registry number")
    void updateIdentity_shouldUpdateFields() {
        // Given
        Agency agency = Agency.create(KERNEL_ORG_ID, TENANT_ID, "Old Name", "OLD-RC", null);

        // When
        agency.updateIdentity("New Name", "NEW-RC-2025");

        // Then
        assertThat(agency.getName()).isEqualTo("New Name");
        assertThat(agency.getCommerceRegistryNumber()).isEqualTo("NEW-RC-2025");
    }

    @Test
    @DisplayName("Agency created with XAF default should not override an explicit null currency with XAF")
    void create_withExplicitXAF_shouldKeepXAF() {
        // When
        Agency agency = Agency.create(KERNEL_ORG_ID, TENANT_ID, "Agency XAF", null, "XAF");

        // Then
        assertThat(agency.getPrimaryCurrency()).isEqualTo("XAF");
    }

    @Test
    @DisplayName("Two agencies created with Agency.create() must have distinct IDs")
    void create_twoAgencies_shouldHaveDistinctIds() {
        // When
        Agency a1 = Agency.create(KERNEL_ORG_ID, TENANT_ID, "Agency 1", null, null);
        Agency a2 = Agency.create(KERNEL_ORG_ID, TENANT_ID, "Agency 2", null, null);

        // Then
        assertThat(a1.getId()).isNotEqualTo(a2.getId());
    }
}
