package com.yowyob.tiibntick.core.agency.assignment.application.service;

import com.yowyob.tiibntick.core.agency.assignment.adapter.out.clients.DeliveryCorePort;
import com.yowyob.tiibntick.core.agency.assignment.adapter.out.persistence.AgencyMissionR2dbcRepository;
import com.yowyob.tiibntick.core.agency.commission.application.service.CommissionService;
import com.yowyob.tiibntick.core.agency.compliance.application.service.ComplianceOrchestrator;
import com.yowyob.tiibntick.core.agency.eventing.application.port.AgencyEventPublisher;
import com.yowyob.tiibntick.core.agency.org.adapter.out.clients.TrustPort;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRelayHubR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRegistryEntity;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyRegistryService;
import com.yowyob.tiibntick.core.agency.org.hubops.application.service.HubParcelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MissionService#createFromIntake}, covering the fix for a bug where
 * {@code AgencyMission.coreMissionId} was a locally-fabricated UUID with no relation to any
 * real {@code tnt-delivery-core} {@code Delivery} — {@code DeliveryMissionClient} used to hit
 * tnt-sales-core's generic order endpoints and discard the response. It must now call
 * {@code DeliveryCorePort.createDelivery} and use the real returned delivery id/trackingCode.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock private AgencyMissionR2dbcRepository missionRepo;
    @Mock private AgencyRegistryService agencyRegistry;
    @Mock private DeliveryCorePort deliveryCore;
    @Mock private MissionDeliveryOrchestrator deliveryOrchestrator;
    @Mock private AgencyRelayHubR2dbcRepository hubRepo;
    @Mock private HubParcelService hubParcelService;
    @Mock private TrustPort trust;
    @Mock private CommissionService commissionService;
    @Mock private AgencyEventPublisher eventPublisher;
    @Mock private ComplianceOrchestrator complianceOrchestrator;

    private MissionService missionService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID AGENCY_ID = UUID.randomUUID();
    private static final UUID INTAKE_ID = UUID.randomUUID();
    private static final UUID REAL_DELIVERY_ID = UUID.randomUUID();
    private static final String REAL_TRACKING_CODE = "TNT-20260727-A1B2C3D4";

    @BeforeEach
    void setUp() {
        missionService = new MissionService(
                missionRepo, agencyRegistry, deliveryCore, deliveryOrchestrator,
                hubRepo, hubParcelService, trust, commissionService,
                eventPublisher, complianceOrchestrator);
    }

    @Test
    @DisplayName("createFromIntake() sets coreMissionId to the real Delivery id returned by tnt-delivery-core")
    void createFromIntakeUsesRealDeliveryIdAsCoreMissionId() {
        when(agencyRegistry.getById(TENANT_ID, AGENCY_ID))
                .thenReturn(Mono.just(AgencyRegistryEntity.builder().build()));
        when(deliveryCore.createDelivery(any())).thenReturn(Mono.just(
                new DeliveryCorePort.DeliveryView(REAL_DELIVERY_ID, "CREATED", REAL_TRACKING_CODE,
                        null, null, null)));
        when(missionRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        MissionService.CreateFromIntakeInput input = new MissionService.CreateFromIntakeInput(
                TENANT_ID, AGENCY_ID, UUID.randomUUID(),
                "Rue de la Joie", "Rue du Marché",
                "Jean Client", "Marie Recipient", "+237690000001",
                2.5, 1, null, INTAKE_ID);

        StepVerifier.create(missionService.createFromIntake(input))
                .assertNext(result -> {
                    assertThat(result.mission().getCoreMissionId()).isEqualTo(REAL_DELIVERY_ID);
                    assertThat(result.trackingCode()).isEqualTo(REAL_TRACKING_CODE);
                    // The agency's own local mission id must stay distinct from coreMissionId,
                    // matching the sound MissionController.create() path.
                    assertThat(result.mission().getId()).isNotEqualTo(result.mission().getCoreMissionId());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("createFromIntake() sends the intake request's own id as senderId (no real actor UUID available)")
    void createFromIntakeUsesIntakeIdAsSenderId() {
        when(agencyRegistry.getById(TENANT_ID, AGENCY_ID))
                .thenReturn(Mono.just(AgencyRegistryEntity.builder().build()));
        when(deliveryCore.createDelivery(any())).thenReturn(Mono.just(
                new DeliveryCorePort.DeliveryView(REAL_DELIVERY_ID, "CREATED", REAL_TRACKING_CODE,
                        null, null, null)));
        when(missionRepo.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        MissionService.CreateFromIntakeInput input = new MissionService.CreateFromIntakeInput(
                TENANT_ID, AGENCY_ID, UUID.randomUUID(),
                "Rue de la Joie", "Rue du Marché",
                "Jean Client", "Marie Recipient", "+237690000001",
                2.5, 1, null, INTAKE_ID);

        missionService.createFromIntake(input).block();

        ArgumentCaptor<DeliveryCorePort.CreateDeliveryRequest> captor =
                ArgumentCaptor.forClass(DeliveryCorePort.CreateDeliveryRequest.class);
        org.mockito.Mockito.verify(deliveryCore).createDelivery(captor.capture());
        assertThat(captor.getValue().senderId()).isEqualTo(INTAKE_ID);
        assertThat(captor.getValue().agencyId()).isEqualTo(AGENCY_ID);
    }
}
