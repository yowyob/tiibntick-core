package com.yowyob.tiibntick.core.organization.domain;

import com.yowyob.tiibntick.core.organization.domain.model.Branch;
import com.yowyob.tiibntick.core.organization.domain.vo.OrganizationId;
import com.yowyob.tiibntick.core.organization.domain.vo.ServiceZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link Branch} domain aggregate.
 *
 * <p>Tests cover: factory creation, Kernel integration key enforcement,
 * activate/deactivate lifecycle, and ServiceZone assignment.
 *
 * @author MANFOUO Braun
 */
@DisplayName("Branch — Domain aggregate tests")
class BranchTest {

    private static final UUID KERNEL_ORG_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final OrganizationId AGENCY_ID = OrganizationId.generate();

    @Test
    @DisplayName("create() should produce an active Branch with generated ID")
    void create_shouldProduceActiveBranch() {
        // When
        Branch branch = Branch.create(
                KERNEL_ORG_ID, AGENCY_ID, TENANT_ID, "Akwa Branch", "Face Marché Central", null);

        // Then
        assertThat(branch.getId()).isNotNull();
        assertThat(branch.getOrganizationId()).isEqualTo(KERNEL_ORG_ID);
        assertThat(branch.getAgencyId()).isEqualTo(AGENCY_ID);
        assertThat(branch.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(branch.getName()).isEqualTo("Akwa Branch");
        assertThat(branch.isActive()).isTrue();
        assertThat(branch.getServiceZone()).isNull();
    }

    @Test
    @DisplayName("constructor should throw when organizationId is null")
    void constructor_shouldThrowWhenOrganizationIdIsNull() {
        assertThatThrownBy(() ->
                Branch.create(null, AGENCY_ID, TENANT_ID, "Branch", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("organizationId");
    }

    @Test
    @DisplayName("constructor should throw when agencyId is null")
    void constructor_shouldThrowWhenAgencyIdIsNull() {
        assertThatThrownBy(() ->
                Branch.create(KERNEL_ORG_ID, null, TENANT_ID, "Branch", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agencyId");
    }

    @Test
    @DisplayName("deactivate() should set active to false")
    void deactivate_shouldMarkBranchInactive() {
        // Given
        Branch branch = Branch.create(KERNEL_ORG_ID, AGENCY_ID, TENANT_ID, "Branch", null, null);

        // When
        branch.deactivate();

        // Then
        assertThat(branch.isActive()).isFalse();
    }

    @Test
    @DisplayName("activate() should restore active status after deactivation")
    void activate_shouldRestoreActiveStatus() {
        // Given
        Branch branch = Branch.create(KERNEL_ORG_ID, AGENCY_ID, TENANT_ID, "Branch", null, null);
        branch.deactivate();

        // When
        branch.activate();

        // Then
        assertThat(branch.isActive()).isTrue();
    }

    @Test
    @DisplayName("assignServiceZone() should set and replace the coverage zone")
    void assignServiceZone_shouldSetZone() {
        // Given
        Branch branch = Branch.create(KERNEL_ORG_ID, AGENCY_ID, TENANT_ID, "Branch", null, null);
        ServiceZone zone = ServiceZone.active(
                "Akwa Coverage", "POLYGON((9.69 4.04, 9.72 4.04, 9.72 4.07, 9.69 4.07, 9.69 4.04))");

        // When
        branch.assignServiceZone(zone);

        // Then
        assertThat(branch.getServiceZone()).isNotNull();
        assertThat(branch.getServiceZone().zoneName()).isEqualTo("Akwa Coverage");
    }

    @Test
    @DisplayName("relocate() should update the address")
    void relocate_shouldUpdateAddress() {
        // Given
        Branch branch = Branch.create(
                KERNEL_ORG_ID, AGENCY_ID, TENANT_ID, "Branch", "Old Address", null);

        // When
        branch.relocate("New Address — Rue Joss, Douala");

        // Then
        assertThat(branch.getAddress()).isEqualTo("New Address — Rue Joss, Douala");
    }
}
