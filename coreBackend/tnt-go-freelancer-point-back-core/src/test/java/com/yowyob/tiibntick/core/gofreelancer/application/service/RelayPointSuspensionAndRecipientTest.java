package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;
import com.yowyob.tiibntick.core.actor.application.port.in.IValidateKycUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.model.ActorStatus;
import com.yowyob.tiibntick.core.actor.domain.model.RelayOperatorProfile;
import com.yowyob.tiibntick.core.geo.application.port.in.IRevokeRelayHubUseCase;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.application.port.in.IManageHubApprovalUseCase;
import com.yowyob.tiibntick.core.geo.domain.exception.RelayHubStateException;
import com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus;
import com.yowyob.tiibntick.core.geo.domain.model.HubStatus;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.AdminRelayPointController;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.EmailPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.application.service.AdminRelayPointApplicationService.RelayPointRevokedException;
import com.yowyob.tiibntick.core.gofreelancer.application.service.AdminRelayPointApplicationService.RelayPointStatusException;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpNotificationTemplates;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Relay point suspension / reactivation wired to the service (they were 200 stubs), on the couriers' machine; status
 * notifications and hub provisioning aimed at the operator's actor, never at a profile id (H1).
 */
class RelayPointSuspensionAndRecipientTest {

    private static final UUID SYSTEM = TenantContextHolder.SYSTEM_TENANT;

    private final GofpRelayPointRepository relayPoints = mock(GofpRelayPointRepository.class);
    private final IRelayOperatorRepository operators = mock(IRelayOperatorRepository.class);
    private final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
    private final PushNotificationPort push = mock(PushNotificationPort.class);
    private final EmailPort email = mock(EmailPort.class);
    private final IRelayHubPort hubs = mock(IRelayHubPort.class);
    private final AddressUseCase addresses = mock(AddressUseCase.class);
    private final IValidateKycUseCase kyc = mock(IValidateKycUseCase.class);
    private final GofpRelayRevocationApplier applier =
            new GofpRelayRevocationApplier(relayPoints, operators, freelancers, push, email);
    private final IManageHubApprovalUseCase approvals = mock(IManageHubApprovalUseCase.class);
    private final AdminRelayPointApplicationService service = new AdminRelayPointApplicationService(
            kyc, email, push, relayPoints, operators, mock(FreelancerVehicleApplicationService.class), hubs, addresses,
            mock(IRevokeRelayHubUseCase.class), applier, approvals);

    private final UUID freelancerProfileId = UUID.randomUUID();
    private final UUID ownerActor = UUID.randomUUID();

    @BeforeEach
    void stubs() {
        doAnswer(inv -> Mono.just(inv.getArgument(0))).when(relayPoints).save(any());
        doAnswer(inv -> Mono.just(inv.getArgument(0))).when(operators).save(any());
        when(relayPoints.findByCoreRelayPointId(any())).thenReturn(Mono.empty());
        when(operators.findByHubId(any(), any())).thenReturn(Mono.empty());
        GofpFreelancer owner = new GofpFreelancer();
        owner.setCoreUserId(ownerActor);
        when(freelancers.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(freelancers.findById(freelancerProfileId)).thenReturn(Mono.just(owner));
        when(push.sendLocalized(any(), any(), anyString(), any())).thenReturn(Mono.empty());
        when(push.sendLocalized(any(UUID.class), anyString(), anyMap())).thenReturn(Mono.empty());
        when(kyc.validateKyc(any())).thenReturn(Mono.empty());
        // the core hub follows the same machine (D-138): by default it accepts and answers with the target status
        when(approvals.approve(any(), any(), any())).thenAnswer(i -> Mono.just(coreHub(i.getArgument(1), HubApprovalStatus.APPROVED)));
        when(approvals.reject(any(), any(), any(), any())).thenAnswer(i -> Mono.just(coreHub(i.getArgument(1), HubApprovalStatus.REJECTED)));
        when(approvals.suspend(any(), any(), any(), any())).thenAnswer(i -> Mono.just(coreHub(i.getArgument(1), HubApprovalStatus.SUSPENDED)));
        when(approvals.reactivate(any(), any(), any())).thenAnswer(i -> Mono.just(coreHub(i.getArgument(1), HubApprovalStatus.APPROVED)));
    }

    private static RelayHub coreHub(UUID id, HubApprovalStatus approval) {
        Instant t = Instant.now();
        return RelayHub.rehydrate(id, SYSTEM, null, RoadNodeId.generate(), 10, 0, "op", HubStatus.ACTIVE, approval,
                t, t, null, null, null);
    }

    private GofpRelayPoint rp(RelayPointStatus status, UUID hubId) {
        GofpRelayPoint rp = GofpRelayPoint.builder().id(UUID.randomUUID()).coreRelayPointId(hubId)
                .coreFreelancerId(freelancerProfileId).name("Relais").ownerEmail("op@example.com")
                .status(status).isActive(status == RelayPointStatus.APPROVED).build();
        when(relayPoints.findById(rp.getId())).thenReturn(Mono.just(rp));
        return rp;
    }

    @Nested
    @DisplayName("suspension and reactivation follow the couriers' machine")
    class Transitions {

        @Test
        void approvedToSuspendedAndBack() {
            UUID hubId = UUID.randomUUID();
            GofpRelayPoint rp = rp(RelayPointStatus.APPROVED, hubId);
            when(operators.findByHubId(SYSTEM, hubId)).thenReturn(Mono.just(
                    RelayOperatorProfile.create(SYSTEM, UUID.randomUUID(), hubId, List.of(), 5).activate()));

            StepVerifier.create(service.suspendRelayPoint(rp.getId(), null)).verifyComplete();
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.SUSPENDED);
            assertThat(rp.getIsActive()).isFalse();
            verify(operators).save(argThat(p -> p.actorStatus() == ActorStatus.SUSPENDED));
            verify(email).sendAccountSuspended("op@example.com", null);

            StepVerifier.create(service.activateRelayPoint(rp.getId(), null)).verifyComplete();
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.APPROVED);
            assertThat(rp.getIsActive()).isTrue();
            verify(operators).save(argThat(p -> p.actorStatus() == ActorStatus.ACTIVE));
        }

        @ParameterizedTest(name = "suspend from {0} refused")
        @EnumSource(value = RelayPointStatus.class, names = {"PENDING", "REJECTED"})
        void suspendRefused(RelayPointStatus from) {
            GofpRelayPoint rp = rp(from, null);
            StepVerifier.create(service.suspendRelayPoint(rp.getId(), null)).expectError(RelayPointStatusException.class).verify();
            assertThat(rp.getStatus()).isEqualTo(from);
            verify(relayPoints, never()).save(any());
        }

        @ParameterizedTest(name = "activate from {0} refused")
        @EnumSource(value = RelayPointStatus.class, names = {"PENDING", "REJECTED"})
        @DisplayName("a PENDING relay point is validated, not activated (no KYC bypass)")
        void activateRefused(RelayPointStatus from) {
            GofpRelayPoint rp = rp(from, null);
            StepVerifier.create(service.activateRelayPoint(rp.getId(), null)).expectError(RelayPointStatusException.class).verify();
            assertThat(rp.getStatus()).isEqualTo(from);
            verify(relayPoints, never()).save(any());
        }

        @Test
        void revokedRefused() {
            GofpRelayPoint rp = rp(RelayPointStatus.REVOKED, null);
            StepVerifier.create(service.suspendRelayPoint(rp.getId(), null)).expectError(RelayPointRevokedException.class).verify();
            StepVerifier.create(service.activateRelayPoint(rp.getId(), null)).expectError(RelayPointRevokedException.class).verify();
        }

        @Test
        @DisplayName("asking for the current status again writes and notifies nothing")
        void sameStatusIsANoOp() {
            GofpRelayPoint suspended = rp(RelayPointStatus.SUSPENDED, null);
            StepVerifier.create(service.suspendRelayPoint(suspended.getId(), null)).verifyComplete();
            GofpRelayPoint approved = rp(RelayPointStatus.APPROVED, null);
            StepVerifier.create(service.activateRelayPoint(approved.getId(), null)).verifyComplete();
            verify(relayPoints, never()).save(any());
            verifyNoInteractions(email);
        }

        @Test
        void unknownRelayPoint() {
            UUID id = UUID.randomUUID();
            when(relayPoints.findById(id)).thenReturn(Mono.empty());
            StepVerifier.create(service.suspendRelayPoint(id, null)).expectError(IllegalArgumentException.class).verify();
            StepVerifier.create(service.activateRelayPoint(id, null)).expectError(IllegalArgumentException.class).verify();
        }
    }

    @Nested
    @DisplayName("H1: the operator's actor is notified, never the freelancer profile id")
    class Recipients {

        @Test
        void operatorProfileActorFirst() {
            UUID hubId = UUID.randomUUID(), operatorActor = UUID.randomUUID();
            GofpRelayPoint rp = rp(RelayPointStatus.APPROVED, hubId);
            when(operators.findByHubId(SYSTEM, hubId)).thenReturn(Mono.just(
                    RelayOperatorProfile.create(SYSTEM, operatorActor, hubId, List.of(), 5).activate()));
            StepVerifier.create(service.suspendRelayPoint(rp.getId(), null)).verifyComplete();
            verify(push).sendLocalized(eq(operatorActor), eq(GofpNotificationTemplates.RELAY_STATUS_SUSPENDED), anyMap());
            verify(push, never()).sendLocalized(eq(freelancerProfileId), anyString(), anyMap());
        }

        @Test
        void ownerFreelancerActorOtherwise() {
            GofpRelayPoint rp = rp(RelayPointStatus.PENDING, null);
            StepVerifier.create(service.validateRelayPoint(rp.getId(), false, "Pièces illisibles", null)).verifyComplete();
            verify(push).sendLocalized(eq(ownerActor), eq(GofpNotificationTemplates.RELAY_STATUS_REJECTED_REASON), anyMap());
            verify(push, never()).sendLocalized(eq(freelancerProfileId), anyString(), anyMap());
        }

        @Test
        @DisplayName("no actor known: nothing is pushed to a profile id, the e-mail still goes")
        void noActorKnown() {
            GofpRelayPoint rp = rp(RelayPointStatus.APPROVED, null);
            rp.setCoreFreelancerId(UUID.randomUUID()); // unknown freelancer
            StepVerifier.create(service.suspendRelayPoint(rp.getId(), null)).verifyComplete();
            verify(push, never()).sendLocalized(any(UUID.class), anyString(), anyMap());
            verify(email).sendAccountSuspended("op@example.com", null);
        }

        @Test
        @DisplayName("a provisioned hub's operator is the owner's actor")
        void provisionedHubOperatorIsTheActor() {
            UUID addressId = UUID.randomUUID();
            GofpRelayPoint rp = rp(RelayPointStatus.PENDING, null);
            rp.setPhysicalAccessAddressId(addressId);
            when(hubs.findHub(isNull(), eq(SYSTEM))).thenReturn(Mono.error(new GeoNotFoundException("RelayHub", "null")));
            AddressDTO dto = new AddressDTO();
            dto.setAddress(Address.ofCoordinates(GeoCoordinates.of(3.86, 11.52), "Yaounde", "CM"));
            when(addresses.getAddressById(addressId)).thenReturn(Mono.just(dto));
            Instant t = Instant.now();
            RelayHub hub = RelayHub.rehydrate(UUID.randomUUID(), SYSTEM, null, RoadNodeId.generate(), 10, 0,
                    ownerActor.toString(), HubStatus.ACTIVE, HubApprovalStatus.PENDING, t, t, null, null, null);
            when(hubs.provisionHub(eq(SYSTEM), any(), anyString(), anyString(), anyInt(), any())).thenReturn(Mono.just(hub));

            StepVerifier.create(service.validateRelayPoint(rp.getId(), true, null, null)).verifyComplete();

            verify(hubs).provisionHub(eq(SYSTEM), any(), eq("Relais"), eq("Yaounde"), eq(10), eq(ownerActor.toString()));
            assertThat(rp.getCoreRelayPointId()).isEqualTo(hub.id());
            verify(approvals).approve(null, hub.id(), null); // D-138: the new (PENDING) hub is approved in the core
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.APPROVED);
        }
    }

    @Nested
    @DisplayName("D-138: the core hub's approval is authoritative, the relay point copies it")
    class CoreApproval {

        @Test
        void suspendAndReactivateGoThroughTheCore() {
            UUID hubId = UUID.randomUUID();
            GofpRelayPoint rp = rp(RelayPointStatus.APPROVED, hubId);
            StepVerifier.create(service.suspendRelayPoint(rp.getId(), null)).verifyComplete();
            verify(approvals).suspend(null, hubId, null, null);
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.SUSPENDED);
            StepVerifier.create(service.activateRelayPoint(rp.getId(), null)).verifyComplete();
            verify(approvals).reactivate(null, hubId, null);
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.APPROVED);
        }

        @Test
        @DisplayName("the core refuses: 409, the relay point is not written nor anyone notified")
        void coreRefusalIsAConflict() {
            UUID hubId = UUID.randomUUID();
            GofpRelayPoint rp = rp(RelayPointStatus.APPROVED, hubId);
            doReturn(Mono.error(new RelayHubStateException("Hub is REVOKED")))
                    .when(approvals).suspend(any(), eq(hubId), any(), any());
            StepVerifier.create(service.suspendRelayPoint(rp.getId(), null)).expectError(RelayPointStatusException.class).verify();
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.APPROVED);
            verify(relayPoints, never()).save(any());
            verifyNoInteractions(email);
        }

        @Test
        @DisplayName("a hub unknown to the core: the relay point follows its own machine alone")
        void hubUnknownToTheCore() {
            UUID hubId = UUID.randomUUID();
            GofpRelayPoint rp = rp(RelayPointStatus.APPROVED, hubId);
            doReturn(Mono.error(new GeoNotFoundException("RelayHub", hubId.toString())))
                    .when(approvals).suspend(any(), eq(hubId), any(), any());
            StepVerifier.create(service.suspendRelayPoint(rp.getId(), null)).verifyComplete();
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.SUSPENDED);
        }

        @Test
        @DisplayName("validation of a PENDING relay point with a hub: approved in the core, then locally")
        void validationApprovesTheCoreHub() {
            UUID hubId = UUID.randomUUID();
            GofpRelayPoint rp = rp(RelayPointStatus.PENDING, hubId);
            when(hubs.findHub(hubId, SYSTEM)).thenReturn(Mono.just(coreHub(hubId, HubApprovalStatus.PENDING)));
            StepVerifier.create(service.validateRelayPoint(rp.getId(), true, null, null)).verifyComplete();
            verify(approvals).approve(null, hubId, null);
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.APPROVED);
            assertThat(rp.getIsActive()).isTrue();

            UUID hub2 = UUID.randomUUID();
            GofpRelayPoint rejected = rp(RelayPointStatus.PENDING, hub2);
            StepVerifier.create(service.validateRelayPoint(rejected.getId(), false, "dossier", null)).verifyComplete();
            verify(approvals).reject(null, hub2, "dossier", null);
            assertThat(rejected.getStatus()).isEqualTo(RelayPointStatus.REJECTED);
        }

        @ParameterizedTest(name = "validate from {0} refused")
        @EnumSource(value = RelayPointStatus.class, names = {"SUSPENDED", "REJECTED", "REVOKED"})
        @DisplayName("only a PENDING relay point is validated (approved or rejected): 409 otherwise")
        void validationOnlyFromPending(RelayPointStatus from) {
            GofpRelayPoint rp = rp(from, UUID.randomUUID());
            StepVerifier.create(service.validateRelayPoint(rp.getId(), true, null, null))
                    .expectError(RelayPointStatusException.class).verify();
            assertThat(rp.getStatus()).isEqualTo(from);
            verify(relayPoints, never()).save(any());
            verifyNoInteractions(approvals);
        }

        @Test
        @DisplayName("validating an APPROVED relay point again changes nothing and calls no legacy fallback")
        void validationRepeatedIsANoOp() {
            GofpRelayPoint rp = rp(RelayPointStatus.APPROVED, UUID.randomUUID());
            StepVerifier.create(service.validateRelayPoint(rp.getId(), true, null, null)).verifyComplete();
            verify(relayPoints, never()).save(any());
            verifyNoInteractions(approvals, kyc, email);
        }

        @Test
        @DisplayName("the generic status change is routed through the machine")
        void changeStatusRouting() {
            UUID hubId = UUID.randomUUID();
            GofpRelayPoint suspended = rp(RelayPointStatus.SUSPENDED, hubId);
            StepVerifier.create(service.changeStatus(suspended.getId(), RelayPointStatus.APPROVED, null, null)).verifyComplete();
            verify(approvals).reactivate(null, hubId, null);
            assertThat(suspended.getStatus()).isEqualTo(RelayPointStatus.APPROVED);

            GofpRelayPoint approved = rp(RelayPointStatus.APPROVED, null);
            StepVerifier.create(service.changeStatus(approved.getId(), RelayPointStatus.SUSPENDED, null, null)).verifyComplete();
            assertThat(approved.getStatus()).isEqualTo(RelayPointStatus.SUSPENDED);

            GofpRelayPoint pending = rp(RelayPointStatus.PENDING, null);
            StepVerifier.create(service.changeStatus(pending.getId(), RelayPointStatus.REJECTED, "x", null)).verifyComplete();
            assertThat(pending.getStatus()).isEqualTo(RelayPointStatus.REJECTED);

            GofpRelayPoint other = rp(RelayPointStatus.APPROVED, null);
            StepVerifier.create(service.changeStatus(other.getId(), RelayPointStatus.PENDING, null, null))
                    .expectError(RelayPointStatusException.class).verify();
            StepVerifier.create(service.changeStatus(other.getId(), RelayPointStatus.REVOKED, null, null))
                    .expectError(IllegalArgumentException.class).verify();
            StepVerifier.create(service.changeStatus(other.getId(), RelayPointStatus.REJECTED, null, null))
                    .expectError(RelayPointStatusException.class).verify();
            assertThat(other.getStatus()).isEqualTo(RelayPointStatus.APPROVED);
        }
    }

    @Nested
    @DisplayName("D-141: a change made through a core route is copied on the Go relay point, once")
    class CoreRoute {

        private final java.util.Map<UUID, RelayHub> hubStore = new java.util.HashMap<>();
        private final com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubRepository hubRepo =
                mock(com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubRepository.class);
        private final org.springframework.beans.factory.support.StaticListableBeanFactory listeners =
                new org.springframework.beans.factory.support.StaticListableBeanFactory();
        private final com.yowyob.tiibntick.core.geo.application.service.RelayHubApprovalService core =
                new com.yowyob.tiibntick.core.geo.application.service.RelayHubApprovalService(hubRepo,
                        listeners.getBeanProvider(com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubApprovalListener.class));
        private final AdminRelayPointApplicationService goFlow = new AdminRelayPointApplicationService(
                kyc, email, push, relayPoints, operators, mock(FreelancerVehicleApplicationService.class), hubs, addresses,
                mock(IRevokeRelayHubUseCase.class), applier, core);

        @BeforeEach
        void wire() {
            when(hubRepo.findById(any(), any())).thenAnswer(i -> Mono.justOrEmpty(hubStore.get(i.<UUID>getArgument(0))));
            when(hubRepo.save(any())).thenAnswer(i -> Mono.just(i.<RelayHub>getArgument(0)));
            listeners.addBean("go", new GofpRelayApprovalApplier(relayPoints, goFlow));
        }

        private GofpRelayPoint linked(RelayPointStatus status, HubApprovalStatus approval) {
            UUID hubId = UUID.randomUUID();
            RelayHub hub = coreHub(hubId, approval);
            hubStore.put(hubId, hub);
            when(hubs.findHub(hubId, SYSTEM)).thenReturn(Mono.just(hub));
            GofpRelayPoint rp = rp(status, hubId);
            when(relayPoints.findByCoreRelayPointId(hubId)).thenReturn(Mono.just(rp));
            return rp;
        }

        @Test
        void coreSuspensionAndApprovalAreCopied() {
            GofpRelayPoint rp = linked(RelayPointStatus.APPROVED, HubApprovalStatus.APPROVED);
            StepVerifier.create(core.suspend(null, rp.getCoreRelayPointId(), "contrôle", UUID.randomUUID()))
                    .expectNextCount(1).verifyComplete();
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.SUSPENDED);
            assertThat(rp.getIsActive()).isFalse();
            verify(email, times(1)).sendAccountSuspended("op@example.com", null);

            GofpRelayPoint pending = linked(RelayPointStatus.PENDING, HubApprovalStatus.PENDING);
            StepVerifier.create(core.approve(null, pending.getCoreRelayPointId(), UUID.randomUUID()))
                    .expectNextCount(1).verifyComplete();
            assertThat(pending.getStatus()).isEqualTo(RelayPointStatus.APPROVED);
            verify(email, times(1)).sendAccountApproved("op@example.com", null);
        }

        @Test
        @DisplayName("a Go route changes the core once and notifies once (the listener stands aside)")
        void goRouteNotifiesOnce() {
            GofpRelayPoint rp = linked(RelayPointStatus.SUSPENDED, HubApprovalStatus.SUSPENDED);
            StepVerifier.create(goFlow.activateRelayPoint(rp.getId(), null)).verifyComplete();
            assertThat(hubStore.get(rp.getCoreRelayPointId()).approvalStatus()).isEqualTo(HubApprovalStatus.APPROVED);
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.APPROVED);
            verify(email, times(1)).sendAccountApproved("op@example.com", null);
            verify(push, times(1)).sendLocalized(any(UUID.class), eq(GofpNotificationTemplates.RELAY_STATUS_REACTIVATED), anyMap());
        }

        @Test
        @DisplayName("a core change on a hub that backs no Go relay point touches nothing in Go")
        void foreignHubIgnored() {
            UUID hubId = UUID.randomUUID();
            hubStore.put(hubId, coreHub(hubId, HubApprovalStatus.APPROVED));
            StepVerifier.create(core.suspend(null, hubId, null, UUID.randomUUID())).expectNextCount(1).verifyComplete();
            verify(relayPoints, never()).save(any());
            verifyNoInteractions(email);
        }
    }

    @Nested
    @DisplayName("AdminRelayPointController /suspend and /activate call the service")
    class Controller {

        private final AdminRelayPointUseCase useCase = mock(AdminRelayPointUseCase.class);
        private final AdminRelayPointController controller = new AdminRelayPointController(useCase);

        @Test
        void delegatesAndMapsErrors() {
            UUID ok = UUID.randomUUID(), missing = UUID.randomUUID(), refused = UUID.randomUUID();
            when(useCase.suspendRelayPoint(ok, "u")).thenReturn(Mono.empty());
            when(useCase.suspendRelayPoint(missing, null))
                    .thenReturn(Mono.error(new IllegalArgumentException("GofpRelayPoint not found: " + missing)));
            when(useCase.activateRelayPoint(refused, null))
                    .thenReturn(Mono.error(new RelayPointStatusException("PENDING")));
            when(useCase.activateRelayPoint(ok, null)).thenReturn(Mono.empty());

            StepVerifier.create(controller.suspendRelayPoint(ok, "u"))
                    .assertNext(r -> assertThat(r.getStatusCode().value()).isEqualTo(200)).verifyComplete();
            StepVerifier.create(controller.suspendRelayPoint(missing, null))
                    .assertNext(r -> assertThat(r.getStatusCode().value()).isEqualTo(404)).verifyComplete();
            StepVerifier.create(controller.activateRelayPoint(refused, null))
                    .assertNext(r -> assertThat(r.getStatusCode().value()).isEqualTo(409)).verifyComplete();
            StepVerifier.create(controller.activateRelayPoint(ok, null))
                    .assertNext(r -> assertThat(r.getStatusCode().value()).isEqualTo(200)).verifyComplete();
            verify(useCase).suspendRelayPoint(ok, "u");
            verify(useCase).activateRelayPoint(ok, null);
        }
    }
}
