package com.yowyob.tiibntick.core.resource.domain;

import com.yowyob.tiibntick.core.resource.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link FreelancerVehicle} domain entity.
 *
 * @author MANFOUO Braun
 */
class FreelancerVehicleTest {

    private UUID orgId;
    private FreelancerVehicle moto;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        moto = FreelancerVehicle.register(orgId, VehicleType.MOTO, "Honda", "CB 125",
                "CMR-001-AB", 80.0, 0.3, FuelType.ESSENCE, 4.5, null, null);
    }

    @Nested
    @DisplayName("Registration")
    class Registration {

        @Test
        @DisplayName("Should register with generated UUID and active status")
        void shouldRegisterWithDefaults() {
            assertThat(moto.vehicleId()).isNotNull();
            assertThat(moto.ownerOrgId()).isEqualTo(orgId);
            assertThat(moto.type()).isEqualTo(VehicleType.MOTO);
            assertThat(moto.isActive()).isTrue();
            assertThat(moto.currentMissionId()).isNull();
            assertThat(moto.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("Should normalize plate number to uppercase")
        void shouldUppercasePlateNumber() {
            FreelancerVehicle v = FreelancerVehicle.register(orgId, VehicleType.MOTO,
                    "Yamaha", "FZ", "cmr-002-cd", 60.0, null, FuelType.ESSENCE, 3.8, null, null);
            assertThat(v.plateNumber()).isEqualTo("CMR-002-CD");
        }

        @Test
        @DisplayName("Should reject blank plate number")
        void shouldRejectBlankPlate() {
            assertThatThrownBy(() -> FreelancerVehicle.register(orgId, VehicleType.VELO,
                    "Giant", "Escape", "  ", 30.0, null, FuelType.ELECTRIQUE, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject negative capacity")
        void shouldRejectNegativeCapacity() {
            assertThatThrownBy(() -> FreelancerVehicle.register(orgId, VehicleType.MOTO,
                    "Honda", "CB", "CMR-003-EF", -10.0, null, FuelType.ESSENCE, 4.0, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Mission assignment")
    class MissionAssignment {

        @Test
        @DisplayName("Should assign vehicle to a mission")
        void shouldAssignToMission() {
            FreelancerVehicle assigned = moto.assignToMission("MISSION-001");
            assertThat(assigned.currentMissionId()).isEqualTo("MISSION-001");
            assertThat(assigned.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("Should fail assignment if already on a mission")
        void shouldFailIfAlreadyOnMission() {
            FreelancerVehicle onMission = moto.assignToMission("MISSION-001");
            assertThatThrownBy(() -> onMission.assignToMission("MISSION-002"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not available");
        }

        @Test
        @DisplayName("Should release vehicle from mission")
        void shouldReleaseFromMission() {
            FreelancerVehicle onMission = moto.assignToMission("MISSION-001");
            FreelancerVehicle released = onMission.releaseFromMission();
            assertThat(released.currentMissionId()).isNull();
            assertThat(released.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("Should fail release if not on mission")
        void shouldFailReleaseIfNotOnMission() {
            assertThatThrownBy(() -> moto.releaseFromMission())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not assigned");
        }
    }

    @Nested
    @DisplayName("Deactivation")
    class Deactivation {

        @Test
        @DisplayName("Should deactivate available vehicle")
        void shouldDeactivate() {
            FreelancerVehicle deactivated = moto.deactivate();
            assertThat(deactivated.isActive()).isFalse();
            assertThat(deactivated.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("Should fail to deactivate vehicle on active mission")
        void shouldFailDeactivateOnMission() {
            FreelancerVehicle onMission = moto.assignToMission("MISSION-001");
            assertThatThrownBy(onMission::deactivate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot deactivate");
        }
    }

    @Nested
    @DisplayName("Capacity checks")
    class CapacityChecks {

        @Test
        @DisplayName("Should accept package within weight capacity")
        void shouldAcceptWithinCapacity() {
            assertThat(moto.canCarry(50.0, 0.2)).isTrue();
        }

        @Test
        @DisplayName("Should reject package exceeding weight capacity")
        void shouldRejectOverweight() {
            assertThat(moto.canCarry(100.0, 0.1)).isFalse();
        }

        @Test
        @DisplayName("Should reject package exceeding volume capacity")
        void shouldRejectOvervolume() {
            assertThat(moto.canCarry(20.0, 1.0)).isFalse(); // volumeM3=0.3
        }
    }

    @Nested
    @DisplayName("Fuel cost estimation")
    class FuelCostEstimation {

        @Test
        @DisplayName("Should estimate fuel cost correctly")
        void shouldEstimateFuelCost() {
            // 10km at 4.5L/100km, 700 XAF/L = 10/100 * 4.5 * 700 = 315 XAF
            double cost = moto.estimateFuelCostXaf(10.0, 700.0);
            assertThat(cost).isEqualTo(315.0, withPrecision(0.01));
        }

        @Test
        @DisplayName("Should return 0 for electric vehicle")
        void shouldReturnZeroForElectric() {
            FreelancerVehicle electric = FreelancerVehicle.register(orgId, VehicleType.VELO,
                    "Trek", "FX3", "CMR-010-EV", 30.0, null, FuelType.ELECTRIQUE, null, null, null);
            assertThat(electric.estimateFuelCostXaf(10.0, 700.0)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should return 0 when fuel consumption is not set")
        void shouldReturnZeroWhenConsumptionNull() {
            FreelancerVehicle noData = FreelancerVehicle.register(orgId, VehicleType.VOITURE,
                    "Toyota", "Corolla", "CMR-011-AA", 300.0, 0.5, FuelType.ESSENCE, null, null, null);
            assertThat(noData.estimateFuelCostXaf(10.0, 700.0)).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("MAX_VEHICLES_PER_ORG constant")
    class FleetLimit {

        @Test
        @DisplayName("Should define max vehicles per org as 3")
        void shouldDefineMaxAsThree() {
            assertThat(FreelancerVehicle.MAX_VEHICLES_PER_ORG).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("New VehicleType enum values")
    class VehicleTypeValues {

        @Test
        @DisplayName("Freelancer-specific vehicle types should exist")
        void shouldHaveFreelancerTypes() {
            assertThat(VehicleType.valueOf("MOTO")).isEqualTo(VehicleType.MOTO);
            assertThat(VehicleType.valueOf("VELO")).isEqualTo(VehicleType.VELO);
            assertThat(VehicleType.valueOf("VOITURE")).isEqualTo(VehicleType.VOITURE);
            assertThat(VehicleType.valueOf("CAMIONNETTE")).isEqualTo(VehicleType.CAMIONNETTE);
            assertThat(VehicleType.valueOf("VELO_CARGO")).isEqualTo(VehicleType.VELO_CARGO);
        }

        @Test
        @DisplayName("Legacy Agency vehicle types must still be present (backward compat)")
        void shouldKeepLegacyTypes() {
            assertThat(VehicleType.valueOf("MOTORCYCLE")).isEqualTo(VehicleType.MOTORCYCLE);
            assertThat(VehicleType.valueOf("BICYCLE")).isEqualTo(VehicleType.BICYCLE);
            assertThat(VehicleType.valueOf("CAR")).isEqualTo(VehicleType.CAR);
            assertThat(VehicleType.valueOf("VAN")).isEqualTo(VehicleType.VAN);
            assertThat(VehicleType.valueOf("TRUCK")).isEqualTo(VehicleType.TRUCK);
        }
    }

    @Nested
    @DisplayName("New EquipmentType enum values")
    class EquipmentTypeValues {

        @Test
        @DisplayName("Freelancer-specific equipment types should exist")
        void shouldHaveFreelancerEquipmentTypes() {
            assertThat(EquipmentType.valueOf("REFRIGERATED_BOX")).isEqualTo(EquipmentType.REFRIGERATED_BOX);
            assertThat(EquipmentType.valueOf("CARGO_BAG")).isEqualTo(EquipmentType.CARGO_BAG);
            assertThat(EquipmentType.valueOf("WATERPROOF_COVER")).isEqualTo(EquipmentType.WATERPROOF_COVER);
            assertThat(EquipmentType.valueOf("TRACKING_BEACON")).isEqualTo(EquipmentType.TRACKING_BEACON);
            assertThat(EquipmentType.valueOf("THERMAL_BAG")).isEqualTo(EquipmentType.THERMAL_BAG);
            assertThat(EquipmentType.valueOf("FRAGILE_FOAM")).isEqualTo(EquipmentType.FRAGILE_FOAM);
            assertThat(EquipmentType.valueOf("OVERSIZED_RACK")).isEqualTo(EquipmentType.OVERSIZED_RACK);
            assertThat(EquipmentType.valueOf("PARCEL_SCANNER")).isEqualTo(EquipmentType.PARCEL_SCANNER);
            assertThat(EquipmentType.valueOf("PADLOCK")).isEqualTo(EquipmentType.PADLOCK);
        }

        @Test
        @DisplayName("Legacy equipment types must still be present (backward compat)")
        void shouldKeepLegacyEquipmentTypes() {
            assertThat(EquipmentType.valueOf("QR_SCANNER")).isEqualTo(EquipmentType.QR_SCANNER);
            assertThat(EquipmentType.valueOf("TABLET")).isEqualTo(EquipmentType.TABLET);
            assertThat(EquipmentType.valueOf("PAYMENT_TERMINAL")).isEqualTo(EquipmentType.PAYMENT_TERMINAL);
            assertThat(EquipmentType.valueOf("GPS_TRACKER")).isEqualTo(EquipmentType.GPS_TRACKER);
        }
    }
}
