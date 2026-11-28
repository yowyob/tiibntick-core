package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;
import com.yowyob.tiibntick.core.actor.application.port.in.IValidateKycUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IRelayOperatorRepository;
import com.yowyob.tiibntick.core.actor.domain.model.KycStatus;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.AddressReactiveRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.EmailPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IRelayHubPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.RelayDepositRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.AdminRelayPointApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerVehicleApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayAutoApproval;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayPointAccessGuard;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayPointDirectoryService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpRelayPoint;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.relaypoint.RelayPointStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lot C-57 — manual relay point approval is suspended until the admin platform exists: a relay
 * point registered by its owner goes through the staff approval path at once (APPROVED, active,
 * KYC VERIFIED, geo hub requested) and is offered to senders. Switch off → PENDING as before.
 * Ownership guards are unchanged.
 */
@DisplayName("C-57 — relay point usable at once (approval suspended)")
class RelayAutoApprovalTest {

    static final UUID OWNER_USER = UUID.fromString("11111111-0000-0000-0000-000000000057");
    static final UUID OWNER_FREELANCER = UUID.fromString("22222222-0000-0000-0000-000000000057");
    static final UUID STRANGER_USER = UUID.fromString("33333333-0000-0000-0000-000000000057");
    static final UUID STRANGER_FREELANCER = UUID.fromString("44444444-0000-0000-0000-000000000057");
    static final UUID ADDRESS = UUID.fromString("cccccccc-0000-0000-0000-000000000057");
    static final String RP_PATH = "/api/v1/gofp/relay-point-profiles";

    /** In-memory relay point store: the use case and the admin service share it. */
    private final Map<UUID, GofpRelayPoint> store = new LinkedHashMap<>();
    private final GofpRelayPointRepository relayPoints = mock(GofpRelayPointRepository.class);
    private final GofpRelayPointUseCase useCase = mock(GofpRelayPointUseCase.class);
    private final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
    private final IValidateKycUseCase kyc = mock(IValidateKycUseCase.class);
    private final EmailPort email = mock(EmailPort.class);
    private final PushNotificationPort push = mock(PushNotificationPort.class);
    private final IRelayHubPort hubs = mock(IRelayHubPort.class);
    private final AddressUseCase addressUseCase = mock(AddressUseCase.class);
    private final com.yowyob.tiibntick.core.geo.application.port.in.IManageHubApprovalUseCase hubApproval =
            mock(com.yowyob.tiibntick.core.geo.application.port.in.IManageHubApprovalUseCase.class);
    private final AddressReactiveRepository addresses = mock(AddressReactiveRepository.class);
    private final com.yowyob.tiibntick.core.gofreelancer.application.service.GofpRelayRevocationApplier revocationApplier =
            mock(com.yowyob.tiibntick.core.gofreelancer.application.service.GofpRelayRevocationApplier.class);

    private Mono<GofpRelayPoint> save(GofpRelayPoint rp) {
        if (rp.getId() == null) rp.setId(UUID.randomUUID());
        if (rp.getCreatedAt() == null) rp.setCreatedAt(Instant.now());
        store.put(rp.getId(), rp);
        return Mono.just(rp);
    }

    @BeforeEach
    void setUp() {
        when(freelancers.findByCoreUserId(any())).thenReturn(Mono.empty());
        when(freelancers.findByCoreUserId(OWNER_USER)).thenReturn(Mono.just(GofpFreelancer.builder()
                .id(OWNER_FREELANCER).coreFreelancerId(OWNER_FREELANCER).coreUserId(OWNER_USER).build()));
        when(freelancers.findByCoreUserId(STRANGER_USER)).thenReturn(Mono.just(GofpFreelancer.builder()
                .id(STRANGER_FREELANCER).coreFreelancerId(STRANGER_FREELANCER).coreUserId(STRANGER_USER).build()));
        when(relayPoints.save(any())).thenAnswer(i -> save(i.getArgument(0)));
        when(relayPoints.findByCoreRelayPointId(any())).thenAnswer(i -> Mono.justOrEmpty(store.values().stream()
                .filter(rp -> i.getArgument(0).equals(rp.getCoreRelayPointId())).findFirst()));
        when(relayPoints.findAllByCoreFreelancerId(any())).thenAnswer(i -> Flux.fromIterable(store.values().stream()
                .filter(rp -> i.getArgument(0).equals(rp.getCoreFreelancerId())).toList()));
        when(relayPoints.findAllByStatus(any())).thenAnswer(i -> Flux.fromIterable(store.values().stream()
                .filter(rp -> rp.getStatus() == i.getArgument(0)).toList()));
        when(useCase.createOrUpdate(any())).thenAnswer(i -> save(i.getArgument(0)));
        // Same filter as GofpRelayPointRepositoryAdapter.findAllActiveApproved: APPROVED and active.
        when(useCase.findActiveApproved()).thenAnswer(i -> Flux.fromIterable(store.values().stream()
                .filter(rp -> rp.getStatus() == RelayPointStatus.APPROVED && Boolean.TRUE.equals(rp.getIsActive())).toList()));
        when(kyc.validateKyc(any())).thenReturn(Mono.empty());
        // D-138 on this branch: the core hub is approved first (authoritative), the relay point copies it.
        when(hubApproval.approve(any(), any(), any())).thenReturn(Mono.empty());
        // H1 on this branch: the hub operator is the owner's actor (none known here).
        when(revocationApplier.operatorActor(any(), any())).thenReturn(Mono.empty());
        when(addresses.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(hubs.findHub(any(), any())).thenReturn(Mono.error(new GeoNotFoundException("RelayHub", "x")));
        when(hubs.provisionHub(any(), any(), anyString(), anyString(), anyInt(), any())).thenReturn(Mono.empty());
        when(addressUseCase.getAddressById(ADDRESS)).thenReturn(Mono.just(AddressDTO.builder().id(ADDRESS)
                .address(Address.builder().street("Rue Joss").city("Douala").country("CM")
                        .coordinates(GeoCoordinates.of(4.05, 9.70)).build()).build()));
    }

    private WebTestClient as(UUID userId, boolean autoApprove) {
        TntSecurityContext ctx = TntSecurityContext.builder().userId(userId).tenantId(UUID.randomUUID())
                .authenticated(true).roles(Set.of("ROLE_FREELANCER")).build();
        RelayPointAccessGuard guard = new RelayPointAccessGuard(relayPoints, freelancers);
        AdminRelayPointApplicationService admin = new AdminRelayPointApplicationService(kyc, email, push, relayPoints,
                mock(IRelayOperatorRepository.class), mock(FreelancerVehicleApplicationService.class), hubs, addressUseCase,
                mock(com.yowyob.tiibntick.core.geo.application.port.in.IRevokeRelayHubUseCase.class),
                revocationApplier,
                hubApproval);
        GofpRelayPointProfileController controller = new GofpRelayPointProfileController(useCase, guard,
                new RelayPointDirectoryService(addresses, mock(IDeliveryNeedRepository.class),
                        mock(RelayDepositRepository.class), mock(IAnnouncementRepository.class)));
        controller.setAutoApproval(new RelayAutoApproval(admin, relayPoints, autoApprove));
        return WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new RelayPointAccessControllerTest.FixedContextResolver(ctx)))
                .httpMessageCodecs(c -> {
                    c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(MAPPER));
                    c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(MAPPER));
                })
                .build();
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .visibility(PropertyAccessor.FIELD, Visibility.ANY)
            .visibility(PropertyAccessor.GETTER, Visibility.NONE)
            .visibility(PropertyAccessor.IS_GETTER, Visibility.NONE)
            .build();

    private static final String BODY = "{\"name\":\"Boutique Akwa\",\"physicalAccessAddressId\":\"" + ADDRESS + "\"}";

    @Test
    @DisplayName("owner registers → APPROVED, active, KYC VERIFIED, geo hub requested, offered to senders; no e-mail")
    void ownerRegistrationIsApprovedAtOnce() {
        as(OWNER_USER, true).put().uri(RP_PATH + "/mine").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("APPROVED").jsonPath("$.isActive").isEqualTo(true);

        GofpRelayPoint saved = store.values().iterator().next();
        assertThat(saved.getStatus()).isEqualTo(RelayPointStatus.APPROVED);
        assertThat(saved.getIsActive()).isTrue();
        verify(hubs).provisionHub(any(), any(), anyString(), anyString(), anyInt(), any());
        verify(kyc).validateKyc(argThat(cmd -> cmd.newKycStatus() == KycStatus.VERIFIED));
        verifyNoInteractions(email, push);

        as(STRANGER_USER, true).get().uri(RP_PATH + "/active-approved").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].name").isEqualTo("Boutique Akwa");
    }

    @Test
    @DisplayName("switch off → PENDING and inactive, as before; nothing provisioned")
    void switchOffKeepsManualApproval() {
        as(OWNER_USER, false).put().uri(RP_PATH + "/mine").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("PENDING").jsonPath("$.isActive").isEqualTo(false);
        verify(hubs, never()).provisionHub(any(), any(), anyString(), anyString(), anyInt(), any());
        verify(kyc, never()).validateKyc(any());
    }

    @Test
    @DisplayName("ownership still holds: a non-owner cannot modify the relay point (403)")
    void nonOwnerStillForbidden() {
        as(OWNER_USER, true).put().uri(RP_PATH + "/mine").contentType(MediaType.APPLICATION_JSON).bodyValue(BODY)
                .exchange().expectStatus().isOk();
        UUID hub = store.values().iterator().next().getCoreRelayPointId();
        as(STRANGER_USER, true).post().uri(RP_PATH).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"coreRelayPointId\":\"" + hub + "\",\"name\":\"Pris\"}")
                .exchange().expectStatus().isForbidden();
        assertThat(store.values().iterator().next().getName()).isEqualTo("Boutique Akwa");
    }

    @Test
    @DisplayName("startup catch-up: PENDING relay points of before become APPROVED; rejected ones are left alone")
    void catchUpPendingAtStartup() {
        save(GofpRelayPoint.builder().coreRelayPointId(UUID.randomUUID()).coreFreelancerId(OWNER_FREELANCER)
                .name("Ancien").status(RelayPointStatus.PENDING).isActive(false).physicalAccessAddressId(ADDRESS).build());
        save(GofpRelayPoint.builder().coreRelayPointId(UUID.randomUUID()).coreFreelancerId(STRANGER_FREELANCER)
                .name("Refusé").status(RelayPointStatus.REJECTED).isActive(false).build());
        AdminRelayPointApplicationService admin = new AdminRelayPointApplicationService(kyc, email, push, relayPoints,
                mock(IRelayOperatorRepository.class), mock(FreelancerVehicleApplicationService.class), hubs, addressUseCase,
                mock(com.yowyob.tiibntick.core.geo.application.port.in.IRevokeRelayHubUseCase.class),
                revocationApplier,
                hubApproval);

        new RelayAutoApproval(admin, relayPoints, true).catchUpPending();

        assertThat(store.values()).filteredOn(rp -> "Ancien".equals(rp.getName()))
                .singleElement().satisfies(rp -> {
                    assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.APPROVED);
                    assertThat(rp.getIsActive()).isTrue();
                });
        assertThat(store.values()).filteredOn(rp -> "Refusé".equals(rp.getName()))
                .singleElement().satisfies(rp -> assertThat(rp.getStatus()).isEqualTo(RelayPointStatus.REJECTED));
    }
}
