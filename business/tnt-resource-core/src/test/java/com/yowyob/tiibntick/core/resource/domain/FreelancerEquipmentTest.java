package com.yowyob.tiibntick.core.resource.domain;

import com.yowyob.tiibntick.core.resource.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link FreelancerEquipment} domain entity.
 *
 * @author MANFOUO Braun
 */
class FreelancerEquipmentTest {

    private UUID orgId;
    private FreelancerEquipment refrigeratedBox;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        refrigeratedBox = FreelancerEquipment.register(orgId, EquipmentType.REFRIGERATED_BOX,
                "Portable 20L cold box", 20.0, OwnershipType.OWNED);
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("Should register with generated UUID and active status")
        void shouldRegisterAsActive() {
            assertThat(refrigeratedBox.equipmentId()).isNotNull();
            assertThat(refrigeratedBox.ownerOrgId()).isEqualTo(orgId);
            assertThat(refrigeratedBox.type()).isEqualTo(EquipmentType.REFRIGERATED_BOX);
            assertThat(refrigeratedBox.isActive()).isTrue();
            assertThat(refrigeratedBox.isAvailable()).isTrue();
            assertThat(refrigeratedBox.currentlyAssignedMissionId()).isNull();
        }

        @Test
        @DisplayName("Should default ownership to OWNED")
        void shouldDefaultToOwned() {
            FreelancerEquipment eq = FreelancerEquipment.register(orgId, EquipmentType.CARGO_BAG,
                    "Large cargo bag", null, null);
            assertThat(eq.ownedOrRented()).isEqualTo(OwnershipType.OWNED);
        }
    }

    @Nested
    @DisplayName("Mission assignment")
    class MissionAssignment {

        @Test
        @DisplayName("Should assign equipment to a mission")
        void shouldAssignToMission() {
            FreelancerEquipment assigned = refrigeratedBox.assignToMission("M-001");
            assertThat(assigned.currentlyAssignedMissionId()).isEqualTo("M-001");
            assertThat(assigned.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("Should fail if already on mission")
        void shouldFailIfBusy() {
            FreelancerEquipment onMission = refrigeratedBox.assignToMission("M-001");
            assertThatThrownBy(() -> onMission.assignToMission("M-002"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("Should release from mission")
        void shouldRelease() {
            FreelancerEquipment onMission = refrigeratedBox.assignToMission("M-001");
            FreelancerEquipment released = onMission.releaseFromMission();
            assertThat(released.currentlyAssignedMissionId()).isNull();
            assertThat(released.isAvailable()).isTrue();
        }
    }

    @Nested
    @DisplayName("isRefrigeratedBox")
    class IsRefrigeratedBox {

        @Test
        @DisplayName("Should return true for REFRIGERATED_BOX type")
        void shouldReturnTrueForRefrigeratedBox() {
            assertThat(refrigeratedBox.isRefrigeratedBox()).isTrue();
        }

        @Test
        @DisplayName("Should return false for other types")
        void shouldReturnFalseForOtherTypes() {
            FreelancerEquipment bag = FreelancerEquipment.register(orgId, EquipmentType.CARGO_BAG,
                    "Bag", null, OwnershipType.RENTED);
            assertThat(bag.isRefrigeratedBox()).isFalse();
        }
    }

    @Nested
    @DisplayName("Deactivation")
    class Deactivation {

        @Test
        @DisplayName("Should deactivate equipment")
        void shouldDeactivate() {
            FreelancerEquipment deactivated = refrigeratedBox.deactivate();
            assertThat(deactivated.isActive()).isFalse();
            assertThat(deactivated.isAvailable()).isFalse();
        }
    }
}
