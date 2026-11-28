package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.IValidateKycUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.model.ActorStatus;
import com.yowyob.tiibntick.core.actor.domain.model.RelayOperatorProfile;
import com.yowyob.tiibntick.core.geo.application.port.in.IRevokeRelayHubUseCase;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.exception.RelayHubStateException;
import com.yowyob.tiibntick.core.geo.domain.model.HubCustodyItem;
import com.yowyob.tiibntick.core.geo.domain.model.HubStatus;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHubRevocation;
import com.yowyob.tiibntick.core.geo.domain.model.RoadNodeId;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.AdminRelayPointController;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.EmailPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.application.service.AdminRelayPointApplicationService.RelayPointRevokedException;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpNotificationTemplates;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.bind.annotation.PutMapping;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Decision D-129: Go-Freelancer relay point revocation, delegated to the core relay hub. */
class RelayPointRevocationTest {

    private static final UUID SYSTEM = TenantContextHolder.SYSTEM_TENANT;
    private static final UUID ADMIN = UUID.randomUUID();

    private final GofpRelayPointRepository relayPoints = mock(GofpRelayPointRepository.class);
    private final IRelayOperatorRepository operators = mock(IRelayOperatorRepository.class);
    private final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
    private final PushNotificationPort push = mock(PushNotificationPort.class);
    private final EmailPort email = mock(EmailPort.class);
    private final IRevokeRelayHubUseCase coreRevoke = mock(IRevokeRelayHubUseCase.class);
    private final GofpRelayRevocationApplier applier =
            new GofpRelayRevocationApplier(relayPoints, operators, freelancers, push, email);
    private final IRelayHubPort hubs = mock(IRelayHubPort.class);
    private final AdminRelayPointApplicationService service = new AdminRelayPointApplicationService(
            mock(IValidateKycUseCase.class), email, push, relayPoints, operators,
            mock(FreelancerVehicleApplicationService.class), hubs, mock(AddressUseCase.class),
            coreRevoke, applier, mock(com.yowyob.tiibntick.core.geo.application.port.in.IManageHubApprovalUseCase.class));

    private GofpRelayPoint rp(RelayPointStatus status, UUID hubId) {
        GofpRelayPoint rp = GofpRelayPoint.builder().id(UUID.randomUUID()).coreRelayPointId(hubId)
                .coreFreelancerId(UUID.randomUUID()).name("Relais").ownerEmail("op@example.com")
                .status(status).isActive(status == RelayPointStatus.APPROVED).build();
        when(relayPoints.findById(rp.getId())).thenReturn(Mono.just(rp));
        when(relayPoints.findByCoreRelayPointId(any())).thenReturn(Mono.empty());
        if (hubId != null) when(relayPoints.findByCoreRelayPointId(hubId)).thenReturn(Mono.just(rp));
        return rp;
    }

    @BeforeEach
    void stubs() {
        doAnswer(inv -> Mono.just(inv.getArgument(0))).when(relayPoints).save(any());
        doAnswer(inv -> Mono.just(inv.getArgument(0))).when(operators).save(any());
        when(operators.findByHubId(any(), any())).thenReturn(Mono.empty());
        when(freelancers.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(push.sendLocalized(any(), any(), anyString(), any())).thenReturn(Mono.empty());
    }

    private static RelayHub revokedHub(UUID hubId, String reason) {
        Instant t = Instant.parse("2026-11-26T08:00:00Z");
        return RelayHub.rehydrate(hubId, SYSTEM, null, RoadNodeId.generate(), 4, 1, "op", HubStatus.ACTIVE,
                com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus.REVOKED, t, t, reason, t, ADMIN);
    }

    @Nested
    @DisplayName("AdminRelayPointApplicationService.revokeRelayPoint")
    class Revoke {

        @Test
        @DisplayName("with a core hub: delegated to the core (reason trimmed, administrator, login URL in context)")
        void delegatesToTheCore() {
            UUID hubId = UUID.randomUUID();
            GofpRelayPoint rp = rp(RelayPointStatus.APPROVED, hubId);
            HubCustodyItem item = new HubCustodyItem(UUID.randomUUID(), UUID.randomUUID(), "TRK", UUID.randomUUID(), Instant.now());
            AtomicReference<String> loginUrlSeen = new AtomicReference<>();
            when(coreRevoke.revoke(null, hubId, "Fraude", ADMIN)).thenReturn(Mono.deferContextual(ctx -> {
                loginUrlSeen.set(ctx.getOrDefault(GofpRelayRevocationApplier.LOGIN_URL_CONTEXT_KEY, null));
                return Mono.just(new RelayHubRevocation(revokedHub(hubId, "Fraude"), List.of(item), true));
            }));

            StepVerifier.create(service.revokeRelayPoint(rp.getId(), "  Fraude ", ADMIN, "https://app/login"))
                    .assertNext(r -> {
                        assertThat(r.relayPointId()).isEqualTo(rp.getId());
                        assertThat(r.hubId()).isEqualTo(hubId);
                        assertThat(r.status()).isEqualTo("REVOKED");
                        assertThat(r.reason()).isEqualTo("Fraude");
                        assertThat(r.coreHubRevoked()).isTrue();
                        assertThat(r.custodyKnown()).isTrue();
                        assertThat(r.parcelsInCustody()).containsExactly(item);
                    })
                    .verifyComplete();
            assertThat(loginUrlSeen.get()).isEqualTo("https://app/login");
        }

        @Test
        @DisplayName("without core hub: revoked locally with the same effects, custody unknown")
        void localWithoutHub() {
            GofpRelayPoint rp = rp(RelayPointStatus.SUSPENDED, null);
            StepVerifier.create(service.revokeRelayPoint(rp.getId(), "Fraude", ADMIN, null))
                    .assertNext(r -> {
                        assertThat(r.coreHubRevoked()).isFalse();
                        assertThat(r.custodyKnown()).isFalse();
                    })
                    .verifyComplete();
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.REVOKED);
            assertThat(rp.getIsActive()).isFalse();
            verifyNoInteractions(coreRevoke);
            verify(email).sendAccountRevoked("op@example.com", null);
        }

        @Test
        @DisplayName("hub unknown to the core, or already revoked there: the relay point is aligned locally")
        void hubUnknownOrAlreadyRevoked() {
            UUID hubId = UUID.randomUUID();
            GofpRelayPoint rp = rp(RelayPointStatus.APPROVED, hubId);
            when(coreRevoke.revoke(null, hubId, "r", ADMIN)).thenReturn(Mono.error(new GeoNotFoundException("RelayHub", "x")));
            StepVerifier.create(service.revokeRelayPoint(rp.getId(), "r", ADMIN, null))
                    .assertNext(r -> assertThat(r.coreHubRevoked()).isFalse()).verifyComplete();
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.REVOKED);

            UUID hub2 = UUID.randomUUID();
            GofpRelayPoint rp2 = rp(RelayPointStatus.APPROVED, hub2);
            when(coreRevoke.revoke(null, hub2, "r", ADMIN)).thenReturn(Mono.error(new RelayHubStateException("revoked")));
            when(hubs.findHub(hub2, null)).thenReturn(Mono.just(revokedHub(hub2, "old")));
            StepVerifier.create(service.revokeRelayPoint(rp2.getId(), "r", ADMIN, null))
                    .assertNext(r -> assertThat(r.coreHubRevoked()).isFalse()).verifyComplete();
            assertThat(rp2.getStatus()).isEqualTo(RelayPointStatus.REVOKED);
        }

        @Test
        @DisplayName("D-138: the core refuses because its hub is not APPROVED/SUSPENDED: 409, the relay point is untouched")
        void coreRefusalStands() {
            UUID hubId = UUID.randomUUID();
            GofpRelayPoint rp = rp(RelayPointStatus.APPROVED, hubId);
            when(coreRevoke.revoke(null, hubId, "r", ADMIN)).thenReturn(Mono.error(new RelayHubStateException("PENDING")));
            Instant t = Instant.now();
            when(hubs.findHub(hubId, null)).thenReturn(Mono.just(RelayHub.rehydrate(hubId, SYSTEM, null,
                    RoadNodeId.generate(), 4, 0, "op", HubStatus.ACTIVE,
                    com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus.PENDING, t, t, null, null, null)));
            StepVerifier.create(service.revokeRelayPoint(rp.getId(), "r", ADMIN, null))
                    .expectError(AdminRelayPointApplicationService.RelayPointStatusException.class).verify();
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.APPROVED);
            verify(relayPoints, never()).save(any());
            verifyNoInteractions(email);
        }

        @ParameterizedTest(name = "{0} -> REVOKED allowed")
        @EnumSource(value = RelayPointStatus.class, names = {"APPROVED", "SUSPENDED"})
        @DisplayName("as for couriers (DeliveryPersonStatus): APPROVED and SUSPENDED may be revoked")
        void approvedOrSuspendedMayBeRevoked(RelayPointStatus from) {
            GofpRelayPoint rp = rp(from, null);
            StepVerifier.create(service.revokeRelayPoint(rp.getId(), "r", ADMIN, null)).expectNextCount(1).verifyComplete();
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.REVOKED);
        }

        @ParameterizedTest(name = "{0} -> REVOKED refused")
        @EnumSource(value = RelayPointStatus.class, names = {"APPROVED", "SUSPENDED", "REVOKED"},
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("as for couriers: a pending relay point is rejected, not revoked; REJECTED is final")
        void otherStatusesRefused(RelayPointStatus from) {
            UUID hubId = UUID.randomUUID();
            GofpRelayPoint rp = rp(from, hubId);
            StepVerifier.create(service.revokeRelayPoint(rp.getId(), "r", ADMIN, null))
                    .expectError(AdminRelayPointApplicationService.RelayPointStatusException.class).verify();
            assertThat(rp.getStatus()).isEqualTo(from);
            verifyNoInteractions(coreRevoke);
            verify(relayPoints, never()).save(any());
        }

        @Test
        @DisplayName("REVOKED is definitive: revoke, suspend, activate and validate are refused")
        void revokedIsDefinitive() {
            GofpRelayPoint rp = rp(RelayPointStatus.REVOKED, UUID.randomUUID());
            StepVerifier.create(service.revokeRelayPoint(rp.getId(), "again", ADMIN, null))
                    .expectError(RelayPointRevokedException.class).verify();
            StepVerifier.create(service.suspendRelayPoint(rp.getId(), null)).expectError(RelayPointRevokedException.class).verify();
            StepVerifier.create(service.activateRelayPoint(rp.getId(), null)).expectError(RelayPointRevokedException.class).verify();
            StepVerifier.create(service.validateRelayPoint(rp.getId(), true, null, null))
                    .expectError(RelayPointRevokedException.class).verify();
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.REVOKED);
            verify(relayPoints, never()).save(any());
            verifyNoInteractions(coreRevoke);
        }

        @Test
        void reasonAndAdministratorMandatory() {
            GofpRelayPoint rp = rp(RelayPointStatus.APPROVED, null);
            StepVerifier.create(service.revokeRelayPoint(rp.getId(), " ", ADMIN, null)).expectError(IllegalArgumentException.class).verify();
            StepVerifier.create(service.revokeRelayPoint(rp.getId(), null, ADMIN, null)).expectError(IllegalArgumentException.class).verify();
            StepVerifier.create(service.revokeRelayPoint(rp.getId(), "r", null, null)).expectError(IllegalArgumentException.class).verify();
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.APPROVED);
        }
    }

    @Nested
    @DisplayName("GofpRelayRevocationApplier (core listener)")
    class Listener {

        @Test
        @DisplayName("aligns the relay point, deactivates the operator and notifies the operator's actor with the reason")
        void alignsAndNotifies() {
            UUID hubId = UUID.randomUUID(), operatorActor = UUID.randomUUID();
            GofpRelayPoint rp = rp(RelayPointStatus.APPROVED, hubId);
            RelayOperatorProfile profile = RelayOperatorProfile.create(SYSTEM, operatorActor, hubId, List.of(), 5).activate();
            when(operators.findByHubId(SYSTEM, hubId)).thenReturn(Mono.just(profile));

            StepVerifier.create(applier.onRevoked(new RelayHubRevocation(revokedHub(hubId, "Fraude"), List.of(), true))
                            .contextWrite(c -> c.put(GofpRelayRevocationApplier.LOGIN_URL_CONTEXT_KEY, "https://app/login")))
                    .verifyComplete();

            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.REVOKED);
            assertThat(rp.getIsActive()).isFalse();
            verify(operators).save(argThat(p -> p.actorStatus() == ActorStatus.INACTIVE));
            verify(push).sendLocalized(SYSTEM, operatorActor, GofpNotificationTemplates.RELAY_STATUS_REVOKED_REASON,
                    Map.of("reason", "Fraude"));
            verify(email).sendAccountRevoked("op@example.com", "https://app/login");
        }

        @Test
        @DisplayName("no operator profile: the owner freelancer's core user (actor id) is notified")
        void fallsBackToTheFreelancerActor() {
            UUID hubId = UUID.randomUUID(), actor = UUID.randomUUID();
            GofpRelayPoint rp = rp(RelayPointStatus.APPROVED, hubId);
            GofpFreelancer f = new GofpFreelancer();
            f.setCoreUserId(actor);
            when(freelancers.findById(rp.getCoreFreelancerId())).thenReturn(Mono.just(f));

            StepVerifier.create(applier.onRevoked(new RelayHubRevocation(revokedHub(hubId, "r"), List.of(), true)))
                    .verifyComplete();
            verify(push).sendLocalized(eq(SYSTEM), eq(actor), eq(GofpNotificationTemplates.RELAY_STATUS_REVOKED_REASON), any());
        }

        @Test
        @DisplayName("a hub that is not a Go-Freelancer relay point (agency hub) is left alone")
        void agencyHubIgnored() {
            when(relayPoints.findByCoreRelayPointId(any())).thenReturn(Mono.empty());
            StepVerifier.create(applier.onRevoked(new RelayHubRevocation(revokedHub(UUID.randomUUID(), "r"), List.of(), true)))
                    .verifyComplete();
            verify(relayPoints, never()).save(any());
            verifyNoInteractions(push, email);
        }

        @Test
        @DisplayName("a failing notification does not fail the revocation")
        void notificationFailureSwallowed() {
            UUID hubId = UUID.randomUUID();
            GofpRelayPoint rp = rp(RelayPointStatus.APPROVED, hubId);
            when(operators.findByHubId(SYSTEM, hubId))
                    .thenReturn(Mono.just(RelayOperatorProfile.create(SYSTEM, UUID.randomUUID(), hubId, List.of(), 1)));
            when(push.sendLocalized(any(), any(), anyString(), any())).thenReturn(Mono.error(new RuntimeException("down")));
            doThrow(new RuntimeException("smtp down")).when(email).sendAccountRevoked(anyString(), any());
            StepVerifier.create(applier.onRevoked(new RelayHubRevocation(revokedHub(hubId, "r"), List.of(), true)))
                    .verifyComplete();
            assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.REVOKED);
        }
    }

    @Test
    @DisplayName("the Go route PUT /api/v1/admin/tnt-go-freelancer/relay-points/{id}/revoke requires relay:revoke")
    void goRoutePermission() throws Exception {
        var method = java.util.Arrays.stream(AdminRelayPointController.class.getMethods())
                .filter(m -> m.getName().equals("revokeRelayPoint")).findFirst().orElseThrow();
        RequirePermission p = method.getAnnotation(RequirePermission.class);
        assertThat(p.resource() + ":" + p.action()).isEqualTo("relay:revoke");
        assertThat(method.getAnnotation(PutMapping.class).value()).containsExactly("/{id}/revoke");
    }
}
