package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RelayPointSubscriptionStatusDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.RelayPointSubscriptionUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.GofpFileUploadService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayPointAccessGuard;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayPointVisuals;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot C-49 — the photos and the subscription of a relay point belong to its owner: uploaded, read and
 * cancelled by the owner or staff; creating a subscription (which takes no payment) is staff-only.
 */
@DisplayName("Relay point photos and subscription — owner or staff")
class RelayPointFilesAndSubscriptionAccessTest {

    static final UUID OWNER_USER = UUID.fromString("11111111-0000-0000-0000-000000000049");
    static final UUID OWNER_FREELANCER = UUID.fromString("22222222-0000-0000-0000-000000000049");
    static final UUID STRANGER_USER = UUID.fromString("33333333-0000-0000-0000-000000000049");
    static final UUID STRANGER_FREELANCER = UUID.fromString("44444444-0000-0000-0000-000000000049");
    static final UUID STAFF_USER = UUID.fromString("55555555-0000-0000-0000-000000000049");
    static final UUID RP = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000049");
    static final UUID HUB = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000049");

    private final GofpFileUploadService uploads = mock(GofpFileUploadService.class);
    private final RelayPointSubscriptionUseCase subscriptions = mock(RelayPointSubscriptionUseCase.class);
    private final GofpRelayPointRepository relayPoints = mock(GofpRelayPointRepository.class);
    private final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);

    private GofpRelayPoint ownersPoint() {
        return GofpRelayPoint.builder().id(RP).coreRelayPointId(HUB).coreFreelancerId(OWNER_FREELANCER)
                .name("Boutique").status(RelayPointStatus.APPROVED).isActive(true).build();
    }

    @BeforeEach
    void setUp() {
        when(freelancers.findByCoreUserId(any())).thenReturn(Mono.empty());
        when(freelancers.findByCoreUserId(OWNER_USER)).thenReturn(Mono.just(GofpFreelancer.builder()
                .id(OWNER_FREELANCER).coreFreelancerId(OWNER_FREELANCER).coreUserId(OWNER_USER).build()));
        when(freelancers.findByCoreUserId(STRANGER_USER)).thenReturn(Mono.just(GofpFreelancer.builder()
                .id(STRANGER_FREELANCER).coreFreelancerId(STRANGER_FREELANCER).coreUserId(STRANGER_USER).build()));
        when(relayPoints.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(relayPoints.findById(RP)).thenAnswer(i -> Mono.just(ownersPoint()));
        when(relayPoints.findByCoreRelayPointId(any())).thenReturn(Mono.empty());
        when(relayPoints.findByCoreRelayPointId(HUB)).thenAnswer(i -> Mono.just(ownersPoint()));
        when(relayPoints.findAllByCoreFreelancerId(any())).thenReturn(Flux.empty());

        RelayPointVisuals visuals = RelayPointVisuals.builder().id(UUID.randomUUID()).coreRelayPointId(HUB)
                .storefrontPhotoUrl("/uploads/x.jpg").build();
        when(uploads.uploadStorefrontPhoto(any(), any())).thenReturn(Mono.just(visuals));
        when(uploads.uploadShopPhoto(any(), any())).thenReturn(Mono.just(visuals));
        RelayPointSubscriptionStatusDTO status = RelayPointSubscriptionStatusDTO.builder().build();
        when(subscriptions.getSubscriptionStatus(any())).thenReturn(Mono.just(status));
        when(subscriptions.createOrRenew(any(), any())).thenReturn(Mono.just(status));
        when(subscriptions.cancel(any())).thenReturn(Mono.just(status));
        when(subscriptions.isEligible(any())).thenReturn(Mono.just(true));
    }

    private WebTestClient as(UUID userId, String... roles) {
        TntSecurityContext ctx = TntSecurityContext.builder().userId(userId).tenantId(UUID.randomUUID())
                .authenticated(true).roles(Set.of(roles)).build();
        RelayPointAccessGuard guard = new RelayPointAccessGuard(relayPoints, freelancers);
        return WebTestClient.bindToController(
                        new GofpFileUploadController(uploads, guard,
                                mock(com.yowyob.tiibntick.core.gofreelancer.application.service.CourierVehicleService.class)),
                        new RelayPointSubscriptionController(subscriptions, guard))
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new RelayPointAccessControllerTest.FixedContextResolver(ctx)))
                .build();
    }

    private static MultipartBodyBuilder photo() {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(new byte[] {1, 2, 3}) {
            @Override public String getFilename() { return "p.jpg"; }
        }).contentType(MediaType.IMAGE_JPEG);
        return body;
    }

    private WebTestClient.ResponseSpec upload(WebTestClient client, UUID id, String kind) {
        return client.post().uri("/api/v1/gofp/files/relay-points/{id}/" + kind, id)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(photo().build()))
                .exchange();
    }

    @Test
    @DisplayName("a stranger cannot upload the storefront or shop photo of someone else's relay point")
    void strangerCannotUpload() {
        upload(as(STRANGER_USER), HUB, "storefront-photo").expectStatus().isForbidden();
        upload(as(STRANGER_USER), HUB, "shop-photo").expectStatus().isForbidden();
        verify(uploads, never()).uploadStorefrontPhoto(any(), any());
        verify(uploads, never()).uploadShopPhoto(any(), any());
    }

    @Test
    @DisplayName("the owner uploads the photos of their relay point, stored under its hub id even when addressed by its id")
    void ownerUploads() {
        upload(as(OWNER_USER), RP, "storefront-photo").expectStatus().isOk();
        upload(as(OWNER_USER), HUB, "shop-photo").expectStatus().isOk();
        verify(uploads).uploadStorefrontPhoto(eq(HUB), any());
        verify(uploads).uploadShopPhoto(eq(HUB), any());
    }

    @Test
    @DisplayName("staff may upload the photos of any relay point")
    void staffUploads() {
        upload(as(STAFF_USER, "TNT_ADMIN"), HUB, "storefront-photo").expectStatus().isOk();
    }

    @Test
    @DisplayName("a stranger can neither read, cancel nor create the subscription of someone else's relay point")
    void strangerCannotTouchSubscription() {
        WebTestClient c = as(STRANGER_USER);
        c.get().uri("/api/relay-points/{id}/subscription", HUB).exchange().expectStatus().isForbidden();
        c.get().uri("/api/relay-points/{id}/subscription/eligible", HUB).exchange().expectStatus().isForbidden();
        c.delete().uri("/api/relay-points/{id}/subscription", HUB).exchange().expectStatus().isForbidden();
        c.post().uri("/api/relay-points/{id}/subscription", HUB).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"subscriptionType\":\"BASIC\",\"paymentMethod\":\"CASH\"}")
                .exchange().expectStatus().isForbidden();
        verify(subscriptions, never()).cancel(any());
        verify(subscriptions, never()).createOrRenew(any(), any());
    }

    @Test
    @DisplayName("the owner reads and cancels their subscription but cannot activate one without payment")
    void ownerReadsAndCancels_butCannotCreate() {
        WebTestClient c = as(OWNER_USER);
        c.get().uri("/api/relay-points/{id}/subscription", HUB).exchange().expectStatus().isOk();
        c.get().uri("/api/relay-points/{id}/subscription/eligible", HUB).exchange().expectStatus().isOk();
        c.delete().uri("/api/relay-points/{id}/subscription", HUB).exchange().expectStatus().isOk();
        c.post().uri("/api/relay-points/{id}/subscription", HUB).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"subscriptionType\":\"BASIC\",\"paymentMethod\":\"CASH\"}")
                .exchange().expectStatus().isForbidden();
        verify(subscriptions, never()).createOrRenew(any(), any());
    }
}
