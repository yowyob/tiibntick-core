package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.entity.AddressEntity;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.AddressReactiveRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.RelayDepositUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.RelayDepositRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayPointAccessGuard;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayPointDirectoryService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.RelayDeposit;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.logistics.RelayDepositStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Lot C-48 — relay points are managed by their owner, approval and deletion are staff-only, a relay
 * point created by its owner starts pending, deposits are read by the relay point owner or the client
 * they belong to, and senders see approved relay points without the owner's contact.
 * Real {@link RelayPointAccessGuard} and {@link RelayPointDirectoryService}, mocked ports.
 */
@DisplayName("Relay point profiles and deposits — owner, staff and public view")
class RelayPointAccessControllerTest {

    static final UUID OWNER_USER = UUID.fromString("11111111-0000-0000-0000-000000000048");
    static final UUID OWNER_FREELANCER = UUID.fromString("22222222-0000-0000-0000-000000000048");
    static final UUID STRANGER_USER = UUID.fromString("33333333-0000-0000-0000-000000000048");
    static final UUID STRANGER_FREELANCER = UUID.fromString("44444444-0000-0000-0000-000000000048");
    static final UUID STAFF_USER = UUID.fromString("55555555-0000-0000-0000-000000000048");
    static final UUID RP = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000048");
    static final UUID HUB = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000048");
    static final UUID ADDRESS = UUID.fromString("cccccccc-0000-0000-0000-000000000048");
    static final UUID DEPOSIT = UUID.fromString("dddddddd-0000-0000-0000-000000000048");
    static final UUID CLIENT = UUID.fromString("eeeeeeee-0000-0000-0000-000000000048");
    static final UUID NEED = UUID.fromString("ffffffff-0000-0000-0000-000000000048");

    private final GofpRelayPointUseCase useCase = mock(GofpRelayPointUseCase.class);
    private final RelayDepositUseCase depositUseCase = mock(RelayDepositUseCase.class);
    private final GofpRelayPointRepository relayPoints = mock(GofpRelayPointRepository.class);
    private final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
    private final AddressReactiveRepository addresses = mock(AddressReactiveRepository.class);
    private final IDeliveryNeedRepository needs = mock(IDeliveryNeedRepository.class);
    private final RelayDepositRepository deposits = mock(RelayDepositRepository.class);
    private final IAnnouncementRepository announcements = mock(IAnnouncementRepository.class);

    private GofpRelayPoint ownersPoint() {
        return GofpRelayPoint.builder().id(RP).coreRelayPointId(HUB).coreFreelancerId(OWNER_FREELANCER)
                .name("Boutique Akwa").status(RelayPointStatus.APPROVED).isActive(true)
                .ownerEmail("owner@example.test").ownerPhone("+237600000048").ownerFirstName("O").ownerLastName("W")
                .physicalAccessAddressId(ADDRESS).totalDeposits(7).depositsUsed(3).createdAt(Instant.now()).build();
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
        when(relayPoints.findAllByCoreFreelancerId(OWNER_FREELANCER)).thenAnswer(i -> Flux.just(ownersPoint()));

        when(useCase.createOrUpdate(any())).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(useCase.findById(any())).thenAnswer(i -> Mono.just(ownersPoint()));
        when(useCase.updateStatus(any(), any())).thenAnswer(i -> Mono.just(ownersPoint()));
        when(useCase.setActive(any(), any())).thenAnswer(i -> Mono.just(ownersPoint()));
        when(useCase.delete(any())).thenReturn(Mono.empty());
        when(useCase.findAll()).thenReturn(Flux.just(ownersPoint()));
        when(useCase.findActiveApproved()).thenReturn(Flux.just(ownersPoint()));
        AddressEntity address = new AddressEntity();
        address.setStreet("Rue Joss");
        address.setCity("Douala");
        address.setLatitude(4.05);
        address.setLongitude(9.70);
        when(addresses.findById(ADDRESS)).thenReturn(Mono.just(address));

        RelayDeposit deposit = RelayDeposit.builder().id(DEPOSIT).packetId(NEED).clientId(CLIENT).relayPointId(HUB)
                .status(RelayDepositStatus.DELIVERED).createdAt(Instant.now()).build();
        when(depositUseCase.findById(DEPOSIT)).thenReturn(Mono.just(deposit));
        when(depositUseCase.getDepositsByRelayPointId(any())).thenReturn(Flux.just(deposit));
        when(depositUseCase.getDepositsByClientId(any())).thenReturn(Flux.just(deposit));
        when(depositUseCase.markAsRetrieved(any(), anyString())).thenReturn(Mono.just(deposit));
    }

    private WebTestClient as(UUID userId, String... roles) {
        TntSecurityContext ctx = TntSecurityContext.builder().userId(userId).tenantId(UUID.randomUUID())
                .authenticated(true).roles(Set.of(roles)).build();
        RelayPointAccessGuard guard = new RelayPointAccessGuard(relayPoints, freelancers);
        RelayPointDirectoryService directory = new RelayPointDirectoryService(addresses, needs, deposits, announcements);
        // the real permission aspect and role registry (D-142: PATCH .../status requires gofp-admin:manage)
        org.springframework.aop.aspectj.annotation.AspectJProxyFactory factory =
                new org.springframework.aop.aspectj.annotation.AspectJProxyFactory(
                        new GofpRelayPointProfileController(useCase, guard, directory));
        factory.setProxyTargetClass(true);
        factory.addAspect(new com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect(
                new com.yowyob.tiibntick.core.roles.application.service.TntPermissionEvaluator(
                        mock(com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver.class),
                        new com.yowyob.tiibntick.core.roles.application.service.TntRoleDefinitionRegistry())));
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userId.toString(), "n/a", java.util.Arrays.stream(roles)
                        .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new).toList());
        return WebTestClient.bindToController(
                        (GofpRelayPointProfileController) factory.getProxy(),
                        new RelayDepositController(depositUseCase, guard))
                .controllerAdvice(new GlobalExceptionHandler(),
                        new com.yowyob.tiibntick.core.roles.adapter.in.web.TntRoleExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new FixedContextResolver(ctx)))
                .webFilter((exchange, chain) -> chain.filter(exchange).contextWrite(
                        org.springframework.security.core.context.ReactiveSecurityContextHolder.withAuthentication(auth)))
                .httpMessageCodecs(c -> {
                    c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(PROD_MAPPER));
                    c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(PROD_MAPPER));
                })
                .build();
    }

    /** Same JSON mapper as the application's WebFlux codecs (tnt-bootstrap TntWebFluxConfig). */
    private static final JsonMapper PROD_MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .visibility(PropertyAccessor.FIELD, Visibility.ANY)
            .visibility(PropertyAccessor.GETTER, Visibility.NONE)
            .visibility(PropertyAccessor.IS_GETTER, Visibility.NONE)
            .build();

    private static final String RP_PATH = "/api/v1/gofp/relay-point-profiles";

    @Test
    @DisplayName("someone else cannot edit, pause or resize an owner's relay point")
    void strangerCannotEdit() {
        as(STRANGER_USER, "ROLE_FREELANCER").post().uri(RP_PATH).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"coreRelayPointId\":\"" + HUB + "\",\"name\":\"Pris\"}")
                .exchange().expectStatus().isForbidden();
        as(STRANGER_USER, "ROLE_FREELANCER").patch().uri(RP_PATH + "/{id}/active?active=false", RP)
                .exchange().expectStatus().isForbidden();
        as(STRANGER_USER, "ROLE_FREELANCER").patch().uri(RP_PATH + "/{id}/storage", RP)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"storageLength\":1}")
                .exchange().expectStatus().isForbidden();
        verify(useCase, never()).createOrUpdate(any());
        verify(useCase, never()).setActive(any(), any());

        as(OWNER_USER, "ROLE_FREELANCER").patch().uri(RP_PATH + "/{id}/active?active=false", RP)
                .exchange().expectStatus().isOk();
        verify(useCase).setActive(RP, false);
    }

    @Test
    @DisplayName("approval needs gofp-admin:manage (D-142), deletion is staff-only, even for the owner")
    void approvalAndDeletionAreStaffOnly() {
        as(STAFF_USER, "ROLE_SUPPORT_AGENT").patch().uri(RP_PATH + "/{id}/status?status=APPROVED", RP)
                .exchange().expectStatus().isForbidden();
        as(STRANGER_USER, "ROLE_FREELANCER").patch().uri(RP_PATH + "/{id}/status?status=APPROVED", RP)
                .exchange().expectStatus().isForbidden();
        as(OWNER_USER, "ROLE_FREELANCER").patch().uri(RP_PATH + "/{id}/status?status=APPROVED", RP)
                .exchange().expectStatus().isForbidden();
        as(STRANGER_USER, "ROLE_FREELANCER").delete().uri(RP_PATH + "/{id}", RP)
                .exchange().expectStatus().isForbidden();
        as(OWNER_USER, "ROLE_FREELANCER").get().uri(RP_PATH + "/by-status/PENDING")
                .exchange().expectStatus().isForbidden();
        verify(useCase, never()).updateStatus(any(), any());
        verify(useCase, never()).delete(any());

        as(STAFF_USER, "ROLE_TNT_ADMIN").patch().uri(RP_PATH + "/{id}/status?status=APPROVED", RP)
                .exchange().expectStatus().isOk();
        as(STAFF_USER, "ROLE_SUPPORT_AGENT").delete().uri(RP_PATH + "/{id}", RP)
                .exchange().expectStatus().isNoContent();
        verify(useCase).updateStatus(RP, RelayPointStatus.APPROVED);
        verify(useCase).delete(RP);
    }

    @Test
    @DisplayName("a relay point created by its owner starts pending and inactive, owned by the caller")
    void createdPending() {
        as(STRANGER_USER, "ROLE_FREELANCER").post().uri(RP_PATH).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Mon relais\",\"status\":\"APPROVED\",\"isActive\":true,\"totalDeposits\":99,"
                        + "\"coreFreelancerId\":\"" + OWNER_FREELANCER + "\"}")
                .exchange().expectStatus().isCreated();
        ArgumentCaptor<GofpRelayPoint> saved = ArgumentCaptor.forClass(GofpRelayPoint.class);
        verify(useCase).createOrUpdate(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(RelayPointStatus.PENDING);
        assertThat(saved.getValue().getIsActive()).isFalse();
        assertThat(saved.getValue().getTotalDeposits()).isZero();
        assertThat(saved.getValue().getCoreFreelancerId()).isEqualTo(STRANGER_FREELANCER);
        assertThat(saved.getValue().getCoreRelayPointId()).isNotNull();
    }

    @Test
    @DisplayName("the owner edits their own relay point without touching its status or counters")
    void ownerEditKeepsStatus() {
        as(OWNER_USER, "ROLE_FREELANCER").put().uri(RP_PATH + "/mine").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Nouveau nom\",\"status\":\"PENDING\",\"isActive\":false,\"depositsUsed\":0}")
                .exchange().expectStatus().isOk();
        ArgumentCaptor<GofpRelayPoint> saved = ArgumentCaptor.forClass(GofpRelayPoint.class);
        verify(useCase).createOrUpdate(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Nouveau nom");
        assertThat(saved.getValue().getStatus()).isEqualTo(RelayPointStatus.APPROVED);
        assertThat(saved.getValue().getIsActive()).isTrue();
        assertThat(saved.getValue().getDepositsUsed()).isEqualTo(3);
        assertThat(saved.getValue().getCoreRelayPointId()).isEqualTo(HUB);
        assertThat(saved.getValue().getCoreFreelancerId()).isEqualTo(OWNER_FREELANCER);
    }

    @Test
    @DisplayName("my relay point: the caller's own, 404 when the caller has none")
    void mine() {
        as(OWNER_USER, "ROLE_FREELANCER").get().uri(RP_PATH + "/mine").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.id").isEqualTo(RP.toString());
        as(STRANGER_USER, "ROLE_FREELANCER").get().uri(RP_PATH + "/mine").exchange().expectStatus().isNotFound();
    }

    @Test
    @DisplayName("expected parcels: needs targeting my relay point, minus cancelled and already deposited")
    void expectedParcels() {
        UUID other = UUID.randomUUID();
        UUID cancelled = UUID.randomUUID();
        when(deposits.findByRelayPointId(HUB)).thenReturn(Flux.just(RelayDeposit.builder().packetId(NEED).build()));
        when(needs.findAllByTargetRelayPointIdIn(any())).thenReturn(Flux.just(
                DeliveryNeed.builder().id(NEED).title("Déposé").status(DeliveryNeedStatus.ASSIGNED).build(),
                DeliveryNeed.builder().id(other).title("Attendu").status(DeliveryNeedStatus.ASSIGNED).build(),
                DeliveryNeed.builder().id(cancelled).title("Annulé").status(DeliveryNeedStatus.CANCELLED).build()));
        when(announcements.findByDeliveryNeedId(any())).thenReturn(Mono.empty());

        as(OWNER_USER, "ROLE_FREELANCER").get().uri(RP_PATH + "/mine/expected-parcels").exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].deliveryNeedId").isEqualTo(other.toString());
    }

    @Test
    @DisplayName("deposits of a relay point: its owner or staff; deposits of a client: that client or staff")
    void depositReads() {
        as(STRANGER_USER, "ROLE_FREELANCER").get().uri("/api/relay-deposits/relay-point/{id}", HUB)
                .exchange().expectStatus().isForbidden();
        as(STRANGER_USER, "ROLE_CLIENT").get().uri("/api/relay-deposits/client/{id}", CLIENT)
                .exchange().expectStatus().isForbidden();
        verify(depositUseCase, never()).getDepositsByRelayPointId(any());
        verify(depositUseCase, never()).getDepositsByClientId(any());

        as(OWNER_USER, "ROLE_FREELANCER").get().uri("/api/relay-deposits/relay-point/{id}", HUB)
                .exchange().expectStatus().isOk();
        as(OWNER_USER, "ROLE_FREELANCER").get().uri("/api/relay-deposits/mine")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$[0].id").isEqualTo(DEPOSIT.toString());
        as(CLIENT, "ROLE_CLIENT").get().uri("/api/relay-deposits/client/{id}", CLIENT)
                .exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("handing a parcel over is for the relay point that holds it, with the code")
    void retrieve() {
        as(STRANGER_USER, "ROLE_FREELANCER").patch().uri("/api/relay-deposits/{id}/retrieve?otpCode=123456", DEPOSIT)
                .exchange().expectStatus().isForbidden();
        verify(depositUseCase, never()).markAsRetrieved(any(), anyString());

        as(OWNER_USER, "ROLE_FREELANCER").patch().uri("/api/relay-deposits/{id}/retrieve?otpCode=123456", DEPOSIT)
                .exchange().expectStatus().isOk();
        verify(depositUseCase).markAsRetrieved(eq(DEPOSIT), eq("123456"));
    }

    @Test
    @DisplayName("senders see approved relay points with their place, never the owner's contact")
    void publicViewHasNoOwnerContact() {
        String body = new String(as(CLIENT, "ROLE_CLIENT").get().uri(RP_PATH + "/active-approved").exchange()
                .expectStatus().isOk().expectBody().returnResult().getResponseBody());
        assertThat(body).contains("Boutique Akwa").contains("Douala").contains("4.05")
                .doesNotContain("owner@example.test").doesNotContain("+237600000048")
                .doesNotContain(OWNER_FREELANCER.toString());
        as(CLIENT, "ROLE_CLIENT").get().uri(RP_PATH).exchange().expectStatus().isForbidden();
    }

    static class FixedContextResolver implements HandlerMethodArgumentResolver {
        private final TntSecurityContext ctx;

        FixedContextResolver(TntSecurityContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class)
                    && TntSecurityContext.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Mono<Object> resolveArgument(MethodParameter parameter, BindingContext bindingContext,
                                            ServerWebExchange exchange) {
            return Mono.just(ctx);
        }
    }
}
