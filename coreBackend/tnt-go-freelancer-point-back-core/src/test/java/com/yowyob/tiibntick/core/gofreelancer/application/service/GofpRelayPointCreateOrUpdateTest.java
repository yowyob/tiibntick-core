package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves that a second {@code createOrUpdate} on the same {@code coreRelayPointId}
 * issues an UPDATE (isNew=false) instead of a duplicate INSERT (isNew=true → 409).
 */
@ExtendWith(MockitoExtension.class)
class GofpRelayPointCreateOrUpdateTest {

    @Mock private GofpRelayPointRepository relayPointRepository;
    @Mock private GofpFreelancerRepository freelancerRepository;

    private GofpRelayPointService service;

    @BeforeEach
    void setUp() {
        service = new GofpRelayPointService(relayPointRepository, freelancerRepository,
                org.mockito.Mockito.mock(
                        com.yowyob.tiibntick.core.administration.application.port.in.ManageDeliveryThresholdsUseCase.class));
    }

    @Test
    void createOrUpdate_whenRecordExists_savesWithIsNewFalse() {
        UUID coreRelayPointId = UUID.randomUUID();
        UUID existingPk = UUID.randomUUID();

        GofpRelayPoint existing = new GofpRelayPoint();
        existing.setId(existingPk);
        existing.setCoreRelayPointId(coreRelayPointId);
        existing.markNotNew();

        GofpRelayPoint incoming = new GofpRelayPoint();
        incoming.setCoreRelayPointId(coreRelayPointId);
        assertThat(incoming.isNew()).as("incoming entity from @RequestBody is new").isTrue();

        when(relayPointRepository.findByCoreRelayPointId(coreRelayPointId))
                .thenReturn(Mono.just(existing));
        when(relayPointRepository.save(any(GofpRelayPoint.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        // coreFreelancerId not set → hydrate() short-circuits, no freelancerRepository call needed

        StepVerifier.create(service.createOrUpdate(incoming))
                .expectNextMatches(rp -> existingPk.equals(rp.getId()))
                .verifyComplete();

        ArgumentCaptor<GofpRelayPoint> captor = ArgumentCaptor.forClass(GofpRelayPoint.class);
        verify(relayPointRepository).save(captor.capture());
        assertThat(captor.getValue().isNew())
                .as("entity passed to save() must have isNew=false (UPDATE, not INSERT)")
                .isFalse();
        assertThat(captor.getValue().getId()).isEqualTo(existingPk);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("D-138/D-139: the staff status change goes through the approval machine, never a raw write")
    void updateStatusIsRoutedThroughTheMachine() {
        com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase admin =
                org.mockito.Mockito.mock(com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase.class);
        service.setAdminRelayPoints(admin);
        GofpRelayPoint rp = new GofpRelayPoint();
        rp.setId(UUID.randomUUID());
        rp.setStatus(com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus.PENDING);
        when(relayPointRepository.findById(rp.getId())).thenReturn(Mono.just(rp));
        when(admin.changeStatus(rp.getId(),
                com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus.REVOKED, null, null))
                .thenReturn(Mono.error(new IllegalArgumentException("a revocation needs a reason")));
        when(admin.changeStatus(rp.getId(),
                com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus.APPROVED, null, null))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.updateStatus(rp.getId(),
                        com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus.REVOKED))
                .expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(service.updateStatus(rp.getId(),
                        com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus.APPROVED))
                .expectNext(rp).verifyComplete();
        org.mockito.Mockito.verify(relayPointRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("D-144/D-146: the suggestion lists keep only relay points whose core hub is approved, open and not full")
    void suggestionsLeaveOutHubsThatCannotTakeAParcel() {
        com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort hubs =
                org.mockito.Mockito.mock(com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort.class);
        service.setRelayHubs(hubs);
        GofpRelayPoint open = suggested(hubs, com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus.APPROVED,
                com.yowyob.tiibntick.core.geo.domain.model.HubStatus.ACTIVE, 3);
        GofpRelayPoint full = suggested(hubs, com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus.APPROVED,
                com.yowyob.tiibntick.core.geo.domain.model.HubStatus.FULL, 4);
        GofpRelayPoint noSlot = suggested(hubs, com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus.APPROVED,
                com.yowyob.tiibntick.core.geo.domain.model.HubStatus.ACTIVE, 4);
        GofpRelayPoint suspended = suggested(hubs, com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus.SUSPENDED,
                com.yowyob.tiibntick.core.geo.domain.model.HubStatus.ACTIVE, 0);
        GofpRelayPoint closed = suggested(hubs, com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus.APPROVED,
                com.yowyob.tiibntick.core.geo.domain.model.HubStatus.TEMPORARILY_CLOSED, 0);
        GofpRelayPoint noHub = new GofpRelayPoint();
        noHub.setId(UUID.randomUUID());
        noHub.setCoreRelayPointId(UUID.randomUUID());
        when(hubs.findHub(noHub.getCoreRelayPointId(), null)).thenReturn(Mono.error(
                new com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException("RelayHub", "x")));

        when(relayPointRepository.findAllActiveApproved())
                .thenReturn(reactor.core.publisher.Flux.just(open, full, noSlot, suspended, closed, noHub));
        when(relayPointRepository.findAllWithSufficientCapacity(0.027))
                .thenReturn(reactor.core.publisher.Flux.just(full, open, noHub));

        StepVerifier.create(service.findActiveApproved()).expectNext(open).verifyComplete();
        StepVerifier.create(service.findWithSufficientCapacity(0.027)).expectNext(open).verifyComplete();
    }

    private static GofpRelayPoint suggested(com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort hubs,
                                            com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus approval,
                                            com.yowyob.tiibntick.core.geo.domain.model.HubStatus status, int occupancy) {
        GofpRelayPoint rp = new GofpRelayPoint();
        rp.setId(UUID.randomUUID());
        rp.setCoreRelayPointId(UUID.randomUUID());
        java.time.Instant t = java.time.Instant.now();
        when(hubs.findHub(rp.getCoreRelayPointId(), null)).thenReturn(Mono.just(
                com.yowyob.tiibntick.core.geo.domain.model.RelayHub.rehydrate(rp.getCoreRelayPointId(), UUID.randomUUID(),
                        null, com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId.generate(), 4, occupancy, "op",
                        status, approval, t, t, null, null, null)));
        return rp;
    }
}
