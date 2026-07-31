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
}
