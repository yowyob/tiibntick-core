package com.yowyob.tiibntick.core.organization.domain;

import com.yowyob.tiibntick.core.organization.domain.model.HubRelais;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link HubRelais} domain aggregate.
 *
 * <p>Tests cover: factory creation, Kernel integration key enforcement,
 * capacity business rule, operator assignment, and suspend/resume lifecycle.
 *
 * @author MANFOUO Braun
 */
@DisplayName("HubRelais — Domain aggregate tests")
class HubRelaisTest {

    private static final UUID KERNEL_ORG_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String DOUALA_WKT = "POINT(9.7022 4.0511)";

    @Test
    @DisplayName("create() should produce an operational HubRelais with generated ID")
    void create_shouldProduceOperationalHub() {
        // When
        HubRelais hub = HubRelais.create(
                KERNEL_ORG_ID, TENANT_ID, "Marché Central", 50, DOUALA_WKT, "Mon-Sat 08:00-18:00", null);

        // Then
        assertThat(hub.getId()).isNotNull();
        assertThat(hub.getOrganizationId()).isEqualTo(KERNEL_ORG_ID);
        assertThat(hub.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(hub.getName()).isEqualTo("Marché Central");
        assertThat(hub.getMaxParcelCapacity()).isEqualTo(50);
        assertThat(hub.getGeographicPointWkt()).isEqualTo(DOUALA_WKT);
        assertThat(hub.isOperational()).isTrue();
    }

    @Test
    @DisplayName("constructor should throw when organizationId is null")
    void constructor_shouldThrowWhenOrganizationIdIsNull() {
        assertThatThrownBy(() ->
                HubRelais.create(null, TENANT_ID, "Hub", 10, DOUALA_WKT, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("organizationId");
    }

    @Test
    @DisplayName("constructor should throw when capacity is zero or negative")
    void constructor_shouldThrowWhenCapacityInvalid() {
        assertThatThrownBy(() ->
                HubRelais.create(KERNEL_ORG_ID, TENANT_ID, "Hub", 0, DOUALA_WKT, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");

        assertThatThrownBy(() ->
                HubRelais.create(KERNEL_ORG_ID, TENANT_ID, "Hub", -5, DOUALA_WKT, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("hasAvailableCapacity() should return true when occupancy is below capacity")
    void hasAvailableCapacity_shouldReturnTrueWhenBelowCapacity() {
        // Given
        HubRelais hub = HubRelais.create(KERNEL_ORG_ID, TENANT_ID, "Hub", 10, DOUALA_WKT, null, null);

        // Then
        assertThat(hub.hasAvailableCapacity(9)).isTrue();
        assertThat(hub.hasAvailableCapacity(10)).isFalse();
        assertThat(hub.hasAvailableCapacity(0)).isTrue();
    }

    @Test
    @DisplayName("suspend() should set operational to false")
    void suspend_shouldMarkHubAsNonOperational() {
        // Given
        HubRelais hub = HubRelais.create(KERNEL_ORG_ID, TENANT_ID, "Hub", 10, DOUALA_WKT, null, null);

        // When
        hub.suspend();

        // Then
        assertThat(hub.isOperational()).isFalse();
    }

    @Test
    @DisplayName("resume() should set operational back to true after suspend")
    void resume_shouldRestoreOperationalStatus() {
        // Given
        HubRelais hub = HubRelais.create(KERNEL_ORG_ID, TENANT_ID, "Hub", 10, DOUALA_WKT, null, null);
        hub.suspend();

        // When
        hub.resume();

        // Then
        assertThat(hub.isOperational()).isTrue();
    }

    @Test
    @DisplayName("assignOperator() should update the operator UUID")
    void assignOperator_shouldUpdateOperatorId() {
        // Given
        HubRelais hub = HubRelais.create(KERNEL_ORG_ID, TENANT_ID, "Hub", 10, DOUALA_WKT, null, null);
        UUID newOperator = UUID.randomUUID();

        // When
        hub.assignOperator(newOperator);

        // Then
        assertThat(hub.getOperatorId()).isEqualTo(newOperator);
    }

    @Test
    @DisplayName("updateCapacity() should reject non-positive values")
    void updateCapacity_shouldRejectNonPositive() {
        // Given
        HubRelais hub = HubRelais.create(KERNEL_ORG_ID, TENANT_ID, "Hub", 10, DOUALA_WKT, null, null);

        // Then
        assertThatThrownBy(() -> hub.updateCapacity(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
