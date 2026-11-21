package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SubscriptionResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AnnouncementUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.service.AnnouncementAccessGuard;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerWhereaboutsResolver;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot C-54b — the applications of an announcement (courier phone, name, plate, vehicle photos) are read
 * only by its owner, the owner of the linked delivery need, or staff; the announcements a courier applied
 * to only by that courier or staff. Strict guard: not driven by {@code tnt.gofp.ownership-guard.enabled}.
 */
@DisplayName("C-54b — applications reserved to the announcement owner")
class AnnouncementApplicationsAccessTest {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000005b4");
    static final UUID ANN = UUID.fromString("aaaaaaaa-0000-0000-0000-0000000005b4");
    static final UUID UNKNOWN_ANN = UUID.fromString("bbbbbbbb-0000-0000-0000-0000000005b4");
    static final UUID OWNER = UUID.fromString("11111111-0000-0000-0000-0000000005b4");
    static final UUID NEED_OWNER = UUID.fromString("22222222-0000-0000-0000-0000000005b4");
    static final UUID STRANGER = UUID.fromString("33333333-0000-0000-0000-0000000005b4");
    static final UUID COURIER_USER = UUID.fromString("44444444-0000-0000-0000-0000000005b4");
    static final UUID COURIER_GOFP = UUID.fromString("55555555-0000-0000-0000-0000000005b4");
    static final UUID COURIER_CORE = UUID.fromString("66666666-0000-0000-0000-0000000005b4");
    static final UUID OTHER_COURIER = UUID.fromString("77777777-0000-0000-0000-0000000005b4");
    static final UUID NEED = UUID.fromString("88888888-0000-0000-0000-0000000005b4");

    private final AnnouncementUseCase useCase = mock(AnnouncementUseCase.class);
    private final IDeliveryAnnouncementPort port = mock(IDeliveryAnnouncementPort.class);
    private final TenantContextHolder tenant = mock(TenantContextHolder.class);
    private final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
    private final IAnnouncementRepository mirror = mock(IAnnouncementRepository.class);
    private final IDeliveryNeedRepository needs = mock(IDeliveryNeedRepository.class);

    @BeforeEach
    void setUp() {
        when(tenant.currentTenantId()).thenReturn(Mono.just(TENANT));
        when(port.findById(any(), any())).thenReturn(Mono.empty());
        when(port.findById(TENANT, ANN)).thenReturn(Mono.just(new AnnouncementSnapshot(
                ANN, TENANT, OWNER, "Colis", "d", AnnouncementStatus.PUBLISHED, Instant.now(), Instant.now(),
                null, "XAF", "QUOTE_REQUEST", Address.builder().street("Rue 1").city("Douala").country("CM").build(),
                Address.builder().street("Rue 1").city("Douala").country("CM").build(), 0.0, null, null, null, null,
                null, null, null, null, "Dest", "+237600000000", null, null, null, List.of())));
        Announcement local = new Announcement();
        local.setId(ANN);
        local.setDeliveryNeedId(NEED);
        when(mirror.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(mirror.findById(ANN)).thenReturn(Mono.just(local));
        when(needs.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(needs.findById(NEED)).thenReturn(Mono.just(DeliveryNeed.builder().id(NEED).userId(NEED_OWNER).build()));
        when(freelancers.findByCoreUserId(any())).thenReturn(Mono.empty());
        when(freelancers.findByCoreUserId(COURIER_USER)).thenReturn(Mono.just(GofpFreelancer.builder()
                .id(COURIER_GOFP).coreFreelancerId(COURIER_CORE).coreUserId(COURIER_USER).build()));
        when(useCase.getSubscriptionsForAnnouncement(any())).thenReturn(Flux.just(new SubscriptionResponseDTO()));
        when(useCase.getSubscriptionsByFreelancerId(any())).thenReturn(Flux.just(new AnnouncementResponseDTO()));
    }

    private WebTestClient as(UUID userId, String... roles) {
        TntSecurityContext ctx = TntSecurityContext.builder().userId(userId).tenantId(TENANT)
                .authenticated(userId != null).roles(Set.of(roles)).build();
        AnnouncementAccessGuard guard = new AnnouncementAccessGuard(port, tenant, freelancers);
        guard.setAnnouncementMirror(mirror);
        guard.setDeliveryNeeds(needs);
        AnnouncementController controller = new AnnouncementController(useCase,
                mock(FreelancerWhereaboutsResolver.class), guard);
        return WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new RelayPointAccessControllerTest.FixedContextResolver(ctx)))
                .build();
    }

    private WebTestClient.ResponseSpec applications(WebTestClient c, UUID announcement) {
        return c.get().uri("/api/announcements/{id}/subscriptions", announcement).exchange();
    }

    private WebTestClient.ResponseSpec applied(WebTestClient c, UUID freelancer) {
        return c.get().uri("/api/announcements/subscriptions/freelancer/{id}", freelancer).exchange();
    }

    @Test
    @DisplayName("a stranger — another sender or a courier — cannot read the applications: 403, nothing read")
    void strangerCannotReadApplications() {
        applications(as(STRANGER, "ROLE_CLIENT"), ANN).expectStatus().isForbidden();
        applications(as(COURIER_USER, "ROLE_FREELANCER"), ANN).expectStatus().isForbidden();
        applications(as(null), ANN).expectStatus().isForbidden();
        verify(useCase, never()).getSubscriptionsForAnnouncement(any());
    }

    @Test
    @DisplayName("the owner of the announcement, the owner of its delivery need and staff read them")
    void ownerNeedOwnerAndStaffRead() {
        applications(as(OWNER, "ROLE_CLIENT"), ANN).expectStatus().isOk();
        applications(as(NEED_OWNER, "ROLE_CLIENT"), ANN).expectStatus().isOk();
        applications(as(STRANGER, "TNT_ADMIN"), ANN).expectStatus().isOk();
        verify(useCase, org.mockito.Mockito.times(3)).getSubscriptionsForAnnouncement(eq(ANN));
    }

    @Test
    @DisplayName("an unknown announcement → 404")
    void unknownAnnouncement() {
        applications(as(OWNER, "ROLE_CLIENT"), UNKNOWN_ANN).expectStatus().isNotFound();
    }

    @Test
    @DisplayName("the announcements a courier applied to: that courier (gofp or core id) or staff only")
    void appliedAnnouncementsReservedToTheCourier() {
        applied(as(COURIER_USER, "ROLE_FREELANCER"), COURIER_GOFP).expectStatus().isOk();
        applied(as(COURIER_USER, "ROLE_FREELANCER"), COURIER_CORE).expectStatus().isOk();
        applied(as(STRANGER, "SUPPORT_AGENT"), OTHER_COURIER).expectStatus().isOk();
        applied(as(COURIER_USER, "ROLE_FREELANCER"), OTHER_COURIER).expectStatus().isForbidden();
        applied(as(OWNER, "ROLE_CLIENT"), COURIER_GOFP).expectStatus().isForbidden();
        // seul l'appel du personnel atteint le service pour OTHER_COURIER ; celui du livreur est refusé
        verify(useCase, org.mockito.Mockito.times(1)).getSubscriptionsByFreelancerId(OTHER_COURIER);
    }
}
