package com.yowyob.tiibntick.core.geo.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code branchId} became nullable so freelance/independent relay points (e.g. GOFP, which
 * by construction has no agency) can be provisioned a real hub — see review finding on
 * {@code RelayHub.create()} previously hard-requiring a branch.
 *
 * @author MANFOUO BRAUN
 */
class RelayHubTest {

    @Test
    void create_withNullBranchId_succeedsForFreelanceHub() {
        RelayHub hub = RelayHub.create(UUID.randomUUID(), null, RoadNodeId.generate(), 10, "actor-1");

        assertThat(hub.branchId()).isNull();
        assertThat(hub.capacitySlots()).isEqualTo(10);
        assertThat(hub.currentOccupancy()).isZero();
        // D-138: a new hub waits for its approval before it is proposed
        assertThat(hub.approvalStatus()).isEqualTo(HubApprovalStatus.PENDING);
        assertThat(hub.isAvailable()).isFalse();
        hub.approve();
        assertThat(hub.isAvailable()).isTrue();
    }

    @Test
    void create_withBranchId_stillWorksForAgencyHub() {
        UUID branchId = UUID.randomUUID();
        RelayHub hub = RelayHub.create(UUID.randomUUID(), branchId, RoadNodeId.generate(), 10, "actor-1");

        assertThat(hub.branchId()).isEqualTo(branchId);
    }

    @Test
    void create_requiresTenantAndNode() {
        assertThatThrownBy(() -> RelayHub.create(null, null, RoadNodeId.generate(), 10, "actor-1"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RelayHub.create(UUID.randomUUID(), null, null, 10, "actor-1"))
                .isInstanceOf(NullPointerException.class);
    }

    // ── D-185: the capacity follows the relay's declared parcel capacity ──

    private static RelayHub hub(int capacity, int occupancy, HubStatus status) {
        return RelayHub.rehydrate(UUID.randomUUID(), UUID.randomUUID(), null, RoadNodeId.generate(), capacity,
                occupancy, "actor-1", status, java.time.Instant.now(), java.time.Instant.now());
    }

    @Test
    void resize_growsAFullHubAndReopensIt() {
        RelayHub hub = hub(10, 10, HubStatus.FULL);
        hub.resize(40);
        assertThat(hub.capacitySlots()).isEqualTo(40);
        assertThat(hub.availableSlots()).isEqualTo(30);
        assertThat(hub.status()).isEqualTo(HubStatus.ACTIVE);
    }

    @Test
    void resize_downToTheParcelsPresentMakesItFull() {
        RelayHub hub = hub(40, 12, HubStatus.ACTIVE);
        hub.resize(12);
        assertThat(hub.status()).isEqualTo(HubStatus.FULL);
    }

    @Test
    void resize_neverBelowTheParcelsPresent() {
        RelayHub hub = hub(40, 12, HubStatus.ACTIVE);
        assertThatThrownBy(() -> hub.resize(11)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("holds 12 parcels");
        assertThat(hub.capacitySlots()).isEqualTo(40);
        assertThatThrownBy(() -> hub.resize(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resize_leavesAClosedHubClosed() {
        RelayHub hub = hub(10, 0, HubStatus.TEMPORARILY_CLOSED);
        hub.resize(25);
        assertThat(hub.capacitySlots()).isEqualTo(25);
        assertThat(hub.status()).isEqualTo(HubStatus.TEMPORARILY_CLOSED);
    }
}
