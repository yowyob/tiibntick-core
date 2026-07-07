package com.yowyob.tiibntick.delivery.domain;

import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.FreelancerRole;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.delivery.domain.event.FreelancerOrgAssignedEvent;
import com.yowyob.tiibntick.core.delivery.domain.event.MissionStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for FreelancerOrg  extensions to the Delivery aggregate.
 *
 * @author MANFOUO Braun
 */
class FreelancerDeliveryTest {

    private Delivery delivery;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        DeliveryAddress addr = new DeliveryAddress("Rue Melen", null, "Centre", "Yaoundé", "CM", null);
        RecipientInfo recip = new RecipientInfo("Alice", "+237600000001", null);
        Parcel parcel = Parcel.create(new PackageSpecification(2.0, 10, 10, 10, false, false, "Test Parcel"));

        delivery = Delivery.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .senderId(UUID.randomUUID())
                .parcel(parcel)
                .pickupAddress(addr)
                .deliveryAddress(addr)
                .recipient(recip)
                .status(DeliveryStatus.CREATED)
                .urgency(DeliveryUrgency.STANDARD)
                .platform("AGENCY")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("assignToFreelancerOrg")
    class AssignToFreelancerOrg {

        @Test
        @DisplayName("Should set FreelancerOrg context and change platform to FREELANCER")
        void shouldAssignFreelancerOrg() {
            String orgId = "FRL-" + UUID.randomUUID();
            delivery.assignToFreelancerOrg(orgId, FreelancerRole.OWNER);

            assertThat(delivery.getAssignedFreelancerOrgId()).isEqualTo(orgId);
            assertThat(delivery.getAssignedFreelancerRole()).isEqualTo(FreelancerRole.OWNER);
            assertThat(delivery.getPlatform()).isEqualTo("FREELANCER");
        }

        @Test
        @DisplayName("Should emit FreelancerOrgAssignedEvent")
        void shouldEmitFreelancerOrgAssignedEvent() {
            String orgId = "FRL-" + UUID.randomUUID();
            delivery.assignToFreelancerOrg(orgId, FreelancerRole.OWNER);

            boolean hasEvent = delivery.getDomainEvents().stream()
                    .anyMatch(e -> e instanceof FreelancerOrgAssignedEvent foa
                            && foa.freelancerOrgId().equals(orgId)
                            && "OWNER".equals(foa.freelancerRole()));
            assertThat(hasEvent).isTrue();
        }

        @Test
        @DisplayName("MissionStatusChangedEvent should contain freelancerOrgId when org is assigned")
        void missionEventShouldContainFreelancerOrgId() {
            String orgId = "FRL-" + UUID.randomUUID();
            delivery.assignToFreelancerOrg(orgId, FreelancerRole.SUB_DELIVERER);
            delivery.clearDomainEvents();

            // Trigger a status change to confirm freelancer context propagates
            delivery.confirmPickup();

            boolean hasMissionEvent = delivery.getDomainEvents().stream()
                    .anyMatch(e -> e instanceof MissionStatusChangedEvent mse
                            && orgId.equals(mse.freelancerOrgId())
                            && "SUB_DELIVERER".equals(mse.freelancerRole()));
            assertThat(hasMissionEvent).isTrue();
        }

        @Test
        @DisplayName("Should throw when freelancerOrgId is null")
        void shouldThrowOnNullOrgId() {
            assertThatThrownBy(() -> delivery.assignToFreelancerOrg(null, FreelancerRole.OWNER))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("recordFreelancerVehicleAssigned")
    class RecordVehicle {

        @Test
        @DisplayName("Should set selectedVehicleId and equipment IDs")
        void shouldRecordVehicle() {
            String vehicleId = UUID.randomUUID().toString();
            List<String> equipIds = List.of("eq1", "eq2");

            delivery.recordFreelancerVehicleAssigned(vehicleId, equipIds);

            assertThat(delivery.getSelectedVehicleId()).isEqualTo(vehicleId);
            assertThat(delivery.getActiveEquipmentIds()).containsExactly("eq1", "eq2");
        }

        @Test
        @DisplayName("Should set empty equipment list when null provided")
        void shouldHandleNullEquipment() {
            delivery.recordFreelancerVehicleAssigned("vehicle-1", null);
            assertThat(delivery.getActiveEquipmentIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("parcel constraints")
    class ParcelConstraints {

        @Test
        @DisplayName("Should set refrigeration, assembly and ID check flags")
        void shouldSetConstraints() {
            delivery.setParcelConstraints(true, false, true);
            assertThat(delivery.isRequiresRefrigeration()).isTrue();
            assertThat(delivery.isRequiresAssembly()).isFalse();
            assertThat(delivery.isRequiresIDCheck()).isTrue();
        }

        @Test
        @DisplayName("Default values should be false")
        void defaultsShouldBeFalse() {
            assertThat(delivery.isRequiresRefrigeration()).isFalse();
            assertThat(delivery.isRequiresAssembly()).isFalse();
            assertThat(delivery.isRequiresIDCheck()).isFalse();
        }
    }

    @Nested
    @DisplayName("deliveryAttemptNumber")
    class AttemptNumber {

        @Test
        @DisplayName("Default attempt number should be 1")
        void defaultShouldBeOne() {
            assertThat(delivery.getDeliveryAttemptNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should increment on each call")
        void shouldIncrement() {
            delivery.incrementDeliveryAttempt();
            assertThat(delivery.getDeliveryAttemptNumber()).isEqualTo(2);
            delivery.incrementDeliveryAttempt();
            assertThat(delivery.getDeliveryAttemptNumber()).isEqualTo(3);
        }
    }
}
