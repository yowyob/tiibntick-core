package com.yowyob.tiibntick.core.resource.domain;

import com.yowyob.tiibntick.core.resource.domain.model.MaintenanceSchedule;
import com.yowyob.tiibntick.core.resource.domain.model.MaintenanceType;
import com.yowyob.tiibntick.core.resource.domain.model.Vehicle;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleStatus;
import com.yowyob.tiibntick.core.resource.domain.model.VehicleType;
import com.yowyob.tiibntick.core.resource.domain.exception.VehicleStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleTest {

    private UUID tenantId;
    private UUID orgId;
    private UUID agencyId;
    private Vehicle availableVehicle;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        agencyId = UUID.randomUUID();
        availableVehicle = Vehicle.register(tenantId, orgId, agencyId,
                "CMR-123-AB", "Yamaha", "FZ25", 2022, VehicleType.MOTORCYCLE, 100.0, 1.5);
    }

    @Test
    void shouldRegisterVehicleWithAvailableStatus() {
        assertThat(availableVehicle.status()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(availableVehicle.registrationNumber()).isEqualTo("CMR-123-AB");
        assertThat(availableVehicle.isAvailable()).isTrue();
    }

    @Test
    void shouldAssignVehicleToDeliverer() {
        UUID delivererId = UUID.randomUUID();
        Vehicle assigned = availableVehicle.assign(delivererId);
        assertThat(assigned.status()).isEqualTo(VehicleStatus.ASSIGNED);
        assertThat(assigned.assignedDelivererId()).isEqualTo(delivererId);
        assertThat(assigned.isAvailable()).isFalse();
    }

    @Test
    void shouldUnassignVehicle() {
        UUID delivererId = UUID.randomUUID();
        Vehicle assigned = availableVehicle.assign(delivererId);
        Vehicle unassigned = assigned.unassign();
        assertThat(unassigned.status()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(unassigned.assignedDelivererId()).isNull();
    }

    @Test
    void shouldRejectAssignmentWhenInMaintenance() {
        MaintenanceSchedule schedule = new MaintenanceSchedule(
                java.time.LocalDate.now().plusDays(7), MaintenanceType.PREVENTIVE, "Routine", 500.0);
        Vehicle inMaintenance = availableVehicle.sendToMaintenance(schedule);
        assertThatThrownBy(() -> inMaintenance.assign(UUID.randomUUID()))
                .isInstanceOf(VehicleStatusTransitionException.class);
    }

    @Test
    void shouldSendAvailableVehicleToMaintenance() {
        MaintenanceSchedule schedule = new MaintenanceSchedule(
                java.time.LocalDate.now().plusDays(7), MaintenanceType.PREVENTIVE, "Oil change", 500.0);
        Vehicle inMaintenance = availableVehicle.sendToMaintenance(schedule);
        assertThat(inMaintenance.status()).isEqualTo(VehicleStatus.IN_MAINTENANCE);
    }

    @Test
    void shouldReturnFromMaintenance() {
        MaintenanceSchedule schedule = new MaintenanceSchedule(
                java.time.LocalDate.now().plusDays(7), MaintenanceType.PREVENTIVE, "Oil change", 500.0);
        Vehicle inMaintenance = availableVehicle.sendToMaintenance(schedule);
        Vehicle returned = inMaintenance.completeMaintenance();
        assertThat(returned.status()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void shouldRetireVehicle() {
        Vehicle retired = availableVehicle.retire();
        assertThat(retired.status()).isEqualTo(VehicleStatus.RETIRED);
    }

    @Test
    void shouldCanCarryWithinCapacity() {
        assertThat(availableVehicle.canCarry(50.0, 0.5)).isTrue();
        assertThat(availableVehicle.canCarry(150.0, 0.5)).isFalse();
        assertThat(availableVehicle.canCarry(50.0, 2.0)).isFalse();
    }

    @Test
    void shouldUpdateOdometer() {
        Vehicle updated = availableVehicle.updateOdometer(1500.5);
        assertThat(updated.odometerKm()).isEqualTo(1500.5);
    }

    @Test
    void shouldDetectMaintenanceDueByOdometer() {
        MaintenanceSchedule schedule = new MaintenanceSchedule(
                java.time.LocalDate.now().plusDays(30), MaintenanceType.PREVENTIVE, "Scheduled", 500.0);
        Vehicle withMaintenance = availableVehicle.scheduleNextMaintenance(schedule);
        Vehicle highOdometer = withMaintenance.updateOdometer(600.0);
        assertThat(highOdometer.isMaintenanceDue()).isTrue();
    }

    @Test
    void shouldRejectDecreasingOdometer() {
        Vehicle updated = availableVehicle.updateOdometer(1500.5);
        assertThatThrownBy(() -> updated.updateOdometer(1000.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
