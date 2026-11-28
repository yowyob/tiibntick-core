package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.DeliveryAnnouncementRepositoryAdapter;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.DeliveryRepositoryAdapter;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcAnnouncementRepository;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcAnnouncementResponseRepository;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcDeliveryRepository;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcParcelRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryCostComputationPort;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryEventPublisher;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryPersonRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.EtaComputationPort;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryAnnouncementService;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryQueryService;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryCost;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PacketDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RespondAnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery.CoreAnnouncementReadAdapter;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery.DeliveryAnnouncementPortAdapter;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.INegotiationChatPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementExtras;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpDeliveryOtpProperties;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot L2 — publication → response → selection → delivery, with every complement that used to live in
 * the gofp mirror, through the REAL Go-Freelancer services, the REAL port adapter, the REAL delivery-core
 * services and repositories, on a real PostgreSQL migrated by the module's Liquibase changelog
 * (including {@code gdd-l2-delivery.yaml}). The only doubles are what the flow does not exercise
 * (cost/ETA engines, event bus, courier quota, chat, gofp profiles).
 */
@Testcontainers
@Tag("integration")
@DisplayName("Annonce Go : publication → réponse → sélection → livraison, compléments relus du core")
class AnnouncementCoreRoundTripIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_gdd_l2_roundtrip")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    static final UUID TENANT = UUID.randomUUID();
    static final UUID CLIENT = UUID.randomUUID();
    static final UUID COURIER = UUID.randomUUID();
    static final UUID HUB = UUID.randomUUID();

    static DeliveryAnnouncementPortAdapter port;
    static AnnouncementApplicationService announcements;
    static NeedAnnouncementService needAnnouncements;
    static DeliveryNeedStateService needState;
    static CoreAnnouncementReadAdapter readView;
    static IDeliveryLifecyclePort lifecycle;
    static TenantContextHolder tenantHolder;

    @BeforeAll
    static void wire() throws Exception {
        try (Connection conn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            new Liquibase("db/changelog/tnt-delivery-master.yaml", new ClassLoaderResourceAccessor(), database)
                    .update(new Contexts(), new LabelExpression());
        }
        R2dbcRepositoryFactory repos = new R2dbcRepositoryFactory(new R2dbcEntityTemplate(new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host(POSTGRES.getHost()).port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                        .database(POSTGRES.getDatabaseName()).username(POSTGRES.getUsername())
                        .password(POSTGRES.getPassword()).build())));
        R2dbcParcelRepository parcels = repos.getRepository(R2dbcParcelRepository.class);
        var parcelWriter = new com.yowyob.tiibntick.core.delivery.adapter.out.persistence.ParcelWriter(parcels);
        var announcementRepo = new DeliveryAnnouncementRepositoryAdapter(
                repos.getRepository(R2dbcAnnouncementRepository.class),
                repos.getRepository(R2dbcAnnouncementResponseRepository.class), parcels, parcelWriter);
        var deliveryRepo = new DeliveryRepositoryAdapter(repos.getRepository(R2dbcDeliveryRepository.class), parcels,
                parcelWriter);

        DeliveryPersonRepository persons = mock(DeliveryPersonRepository.class);
        // A projected courier is PENDING until an administrator approves it (lot L3): only an approved one responds.
        DeliveryPerson approvedCourier = DeliveryPerson.projection(COURIER, TENANT, COURIER, 10);
        approvedCourier.approve();
        when(persons.findById(TENANT, COURIER)).thenReturn(Mono.just(approvedCourier));
        DeliveryCostComputationPort cost = mock(DeliveryCostComputationPort.class);
        when(cost.compute(any(), any(), any(), any())).thenReturn(Mono.just(new DeliveryCost(BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, "XAF")));
        EtaComputationPort eta = mock(EtaComputationPort.class);
        when(eta.computeInitial(any(), any(), anyDouble()))
                .thenReturn(Mono.just(EtaEstimate.of(Instant.now().plusSeconds(3600), 5.0, 60)));
        DeliveryEventPublisher events = mock(DeliveryEventPublisher.class);
        when(events.publishAll(any())).thenReturn(Mono.empty());
        when(events.publish(any())).thenReturn(Mono.empty());

        var thresholds = mock(com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryThresholdsPort.class);
        // no administrator setting: the platform defaults apply (Delivery.DEFAULT_MAX_ATTEMPTS)
        when(thresholds.forAgency(any())).thenReturn(Mono.empty());
        var executor = new com.yowyob.tiibntick.core.delivery.application.service.DeliveryTransitionExecutor(
                deliveryRepo, announcementRepo, events,
                new com.yowyob.tiibntick.core.delivery.application.service.ReturnDeliveryService(announcementRepo,
                        thresholds));
        DeliveryAnnouncementService core = new DeliveryAnnouncementService(announcementRepo, deliveryRepo, persons,
                cost, eta, executor, thresholds);
        DeliveryQueryService query = new DeliveryQueryService(deliveryRepo, announcementRepo, persons);
        port = new DeliveryAnnouncementPortAdapter(core, query, core);

        TenantContextHolder tenant = mock(TenantContextHolder.class);
        when(tenant.currentTenantId()).thenReturn(Mono.just(TENANT));
        tenantHolder = tenant;
        FreelancerQuotaService quota = mock(FreelancerQuotaService.class);
        when(quota.hasRemainingQuota(any())).thenReturn(Mono.just(true));
        INegotiationChatPort chat = mock(INegotiationChatPort.class);
        when(chat.openNegotiationThread(any(), any(), any())).thenReturn(Mono.empty());
        GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
        when(freelancers.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(freelancers.findByCoreFreelancerId(any())).thenReturn(Mono.empty());
        DeliveryRepository gofpDeliveries = mock(DeliveryRepository.class);
        when(gofpDeliveries.findById(any(UUID.class))).thenReturn(Mono.empty());
        when(gofpDeliveries.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        DeliveryOtpService otp = mock(DeliveryOtpService.class);
        when(otp.initOtpIfAbsentWithCodes(any())).thenReturn(Mono.empty());

        lifecycle = mock(IDeliveryLifecyclePort.class);
        // Agreed price recorded on the core delivery before the Go row (lot L1, C2): stubbed here.
        com.yowyob.tiibntick.core.gofreelancer.TestDeliveries.acceptTerms(lifecycle);
        announcements = new AnnouncementApplicationService(port, tenant, quota, chat, freelancers,
                mock(GofpUserRepository.class), gofpDeliveries, otp, mock(GofpDeliveryOtpProperties.class), lifecycle);
        needAnnouncements = new NeedAnnouncementService(port, tenant);
        needState = new DeliveryNeedStateService(port, lifecycle, tenant);
        readView = new CoreAnnouncementReadAdapter(port, tenant);
    }

    private static String sql(String query) throws Exception {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement(); ResultSet rs = s.executeQuery(query)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static AddressDTO address(String street, double lat, double lng) {
        return AddressDTO.builder().address(Address.builder().street(street).city("Douala").country("CM")
                .coordinates(GeoCoordinates.of(lat, lng)).build()).build();
    }

    private static AnnouncementRequestDTO fullRequest() {
        AnnouncementRequestDTO r = new AnnouncementRequestDTO();
        r.setClientId(CLIENT);
        r.setTitle("Colis Akwa → Bonamoussadi");
        r.setDescription("2 kg");
        r.setPricingMode("FIXED_PRICE");
        r.setAmount(2500.0);
        r.setCurrency("XAF");
        r.setUrgency("NORMAL");
        r.setShipperFirstName("Jean");
        r.setShipperLastName("Mbarga Essomba");
        r.setShipperEmail("jean@example.org");
        r.setShipperPhone("+237690000001");
        r.setRecipientFirstName("Marie");
        r.setRecipientLastName("Ngono");
        r.setRecipientEmail("marie@example.org");
        r.setRecipientPhone("+237691000002");
        r.setPaymentMethod("CASH");
        r.setTransportMethod("ROAD");
        r.setRequiredVehicleType("MOTORBIKE");
        r.setDistance(12.5);
        r.setDuration(45);
        r.setDestinationRelayPointId(HUB);
        r.setSignatureUrl("sig://abc");
        r.setLogisticsPrice(300.0);
        r.setPickupAddress(address("Rue Joss", 4.0511, 9.7679));
        r.setDeliveryAddress(address("Carrefour Bonamoussadi", 4.0900, 9.7400));
        PacketDTO packet = new PacketDTO();
        packet.setWeight(2.0);
        packet.setWidth(20.0);
        packet.setHeight(15.0);
        packet.setLength(10.0);
        packet.setDescription("Vêtements");
        r.setPacket(packet);
        return r;
    }

    private static void assertComplements(AnnouncementResponseDTO dto) {
        assertThat(dto.getShipperFirstName()).isEqualTo("Jean");
        assertThat(dto.getShipperLastName()).isEqualTo("Mbarga Essomba");
        assertThat(dto.getShipperEmail()).isEqualTo("jean@example.org");
        assertThat(dto.getShipperPhone()).isEqualTo("+237690000001");
        assertThat(dto.getRecipientFirstName()).isEqualTo("Marie");
        assertThat(dto.getRecipientLastName()).isEqualTo("Ngono");
        assertThat(dto.getRecipientEmail()).isEqualTo("marie@example.org");
        assertThat(dto.getRecipientPhone()).isEqualTo("+237691000002");
        assertThat(dto.getPaymentMethod()).isEqualTo("CASH");
        assertThat(dto.getTransportMethod()).isEqualTo("ROAD");
        assertThat(dto.getRequiredVehicleType()).isEqualTo("MOTORBIKE");
        assertThat(dto.getDistance()).isEqualTo(12.5);
        assertThat(dto.getDuration()).isEqualTo(45);
        assertThat(dto.getDestinationRelayPointId()).isEqualTo(HUB);
        assertThat(dto.getSignatureUrl()).isEqualTo("sig://abc");
        assertThat(dto.getLogisticsPrice()).isEqualTo(300.0);
        assertThat(dto.getAmount()).isEqualTo(2500.0);
        assertThat(dto.getPricingMode()).as("Go = FIXED_PRICE préservé").isEqualTo("FIXED_PRICE");
    }

    @Test
    @DisplayName("tous les compléments sont stockés dans le core et relus après sélection ; la livraison est créée")
    void publishRespondSelectDeliver() throws Exception {
        AnnouncementResponseDTO created = announcements.createAnnouncement(fullRequest()).block();
        UUID id = created.getId();
        assertComplements(created);
        assertThat(sql("SELECT urgency FROM tnt_delivery_announcements WHERE id = '" + id + "'"))
                .as("NORMAL toléré → STANDARD").isEqualTo("STANDARD");
        assertThat(sql("SELECT sender_name FROM tnt_delivery_announcements WHERE id = '" + id + "'"))
                .isEqualTo("Jean Mbarga Essomba");
        assertComplements(announcements.getAnnouncement(id).block());

        RespondAnnouncementRequestDTO respond = new RespondAnnouncementRequestDTO();
        respond.setFreelancerId(COURIER);
        respond.setEstimatedArrivalTime(Instant.now().plusSeconds(1800));
        announcements.respondToAnnouncement(id, respond).block();
        UUID responseId = port.findById(TENANT, id).block().responses().get(0).id();

        AnnouncementResponseDTO assigned = announcements.assignResponse(id, CLIENT, responseId).block();
        assertThat(assigned.getStatus().name()).isEqualTo("ASSIGNED");
        assertThat(assigned.getAssignedFreelancerId()).isEqualTo(COURIER);
        assertThat(assigned.getDeliveryId()).isNotNull();
        assertThat(sql("SELECT count(*) FROM tnt_deliveries WHERE id = '" + assigned.getDeliveryId()
                + "' AND announcement_id = '" + id + "'")).isEqualTo("1");

        AnnouncementResponseDTO reread = announcements.getAnnouncement(id).block();
        assertComplements(reread);
        assertThat(reread.getDeliveryId()).isEqualTo(assigned.getDeliveryId());

        // the courier's "subscriptions" now come from the core responses
        assertThat(announcements.getSubscriptionsByFreelancerId(COURIER).collectList().block())
                .extracting(AnnouncementResponseDTO::getId).contains(id);

        // assigned: payment method and price frozen (409), other complements still editable
        AnnouncementRequestDTO toWallet = new AnnouncementRequestDTO();
        toWallet.setPaymentMethod("WALLET");
        StepVerifier.create(announcements.updateAnnouncement(id, toWallet))
                .expectErrorMatches(e -> e instanceof ResponseStatusException r && r.getStatusCode().value() == 409)
                .verify();
        AnnouncementRequestDTO newSignature = new AnnouncementRequestDTO();
        newSignature.setSignatureUrl("sig://def");
        announcements.updateAnnouncement(id, newSignature).block();
        assertThat(sql("SELECT handover_signature_ref FROM tnt_delivery_announcements WHERE id = '" + id + "'"))
                .isEqualTo("sig://def");
        assertThat(sql("SELECT payment_method FROM tnt_delivery_announcements WHERE id = '" + id + "'")).isEqualTo("CASH");

        // the legacy read view used by delivery/payment services reads the same core row
        Announcement view = readView.findById(id).block();
        assertThat(view.getPaymentMethod()).isEqualTo("CASH");
        assertThat(view.getAssignedFreelancerId()).isEqualTo(COURIER);
    }

    @Test
    @DisplayName("un besoin Go publié : référence d'origine en base, une seule annonce par besoin, statut dérivé")
    void needPublishedWithOriginReference() throws Exception {
        UUID needId = UUID.randomUUID();
        DeliveryNeed need = DeliveryNeed.builder().id(needId).userId(CLIENT).title("Besoin").description("d")
                .paymentMethod("CASH").transportMethod("ROAD").requiredVehicleType("CAR").distance(8.0).build();
        Address pickup = fullRequest().getPickupAddress().getAddress();
        Address drop = fullRequest().getDeliveryAddress().getAddress();

        UUID announcementId = needAnnouncements.publishFor(need, pickup, drop, null).block();
        assertThat(sql("SELECT origin_ref_type || ':' || origin_ref_id FROM tnt_delivery_announcements WHERE id = '"
                + announcementId + "'")).isEqualTo("DELIVERY_NEED:" + needId);
        assertThat(sql("SELECT pricing_mode FROM tnt_delivery_announcements WHERE id = '" + announcementId + "'"))
                .as("Freelancer = QUOTE_REQUEST préservé").isEqualTo("QUOTE_REQUEST");
        assertThat(needAnnouncements.announcementIdOf(needId).block()).isEqualTo(announcementId);
        assertThat(needAnnouncements.needIdOf(announcementId).block()).isEqualTo(needId);
        assertThat(readView.findByDeliveryNeedId(needId).block().getDeliveryNeedId()).isEqualTo(needId);

        StepVerifier.create(needAnnouncements.publishFor(need, pickup, drop, null))
                .as("republication : l'annonce existante est rendue, pas de seconde annonce (ni de 500)")
                .expectNext(announcementId)
                .verifyComplete();
        assertThat(sql("SELECT count(*) FROM tnt_delivery_announcements WHERE origin_ref_id = '" + needId + "'"))
                .isEqualTo("1");

        // Concurrent publication that did not see the first one: the core's unique index refuses it with a
        // translated DataIntegrityViolationException, and the winner is read back.
        DeliveryAnnouncementPortAdapter racing = org.mockito.Mockito.spy(port);
        org.mockito.Mockito.doReturn(Mono.empty()).doCallRealMethod().when(racing).findByOrigin(any(), any(), any());
        StepVerifier.create(new NeedAnnouncementService(racing, tenantHolder).publishFor(need, pickup, drop, null))
                .expectNext(announcementId)
                .verifyComplete();
        org.mockito.Mockito.verify(racing).publish(any());
        assertThat(sql("SELECT count(*) FROM tnt_delivery_announcements WHERE origin_ref_id = '" + needId + "'"))
                .isEqualTo("1");

        assertThat(needState.hydrate(need).block().getStatus()).isEqualTo(DeliveryNeedStatus.PENDING);

        RespondAnnouncementRequestDTO respond = new RespondAnnouncementRequestDTO();
        respond.setFreelancerId(COURIER);
        respond.setProposedPrice(new BigDecimal("3000"));
        respond.setProposedCurrency("XAF");
        announcements.respondToAnnouncement(announcementId, respond).block();
        UUID responseId = port.findById(TENANT, announcementId).block().responses().get(0).id();
        AnnouncementResponseDTO assigned = announcements.assignResponse(announcementId, CLIENT, responseId).block();

        when(lifecycle.findState(TENANT, assigned.getDeliveryId())).thenReturn(Mono.just(
                new IDeliveryLifecyclePort.CoreDeliveryState(assigned.getDeliveryId(), "CREATED", null, COURIER, null, null)));
        DeliveryNeed derived = needState.hydrate(need).block();
        assertThat(derived.getStatus()).isEqualTo(DeliveryNeedStatus.ASSIGNED);
        assertThat(derived.getDeliveryId()).isEqualTo(assigned.getDeliveryId());
        assertThat(derived.getAssignedFreelancerId()).isEqualTo(COURIER);
        assertThat(derived.getAnnouncementId()).isEqualTo(announcementId);
        assertThat(AnnouncementExtras.ORIGIN_DELIVERY_NEED).isEqualTo("DELIVERY_NEED");
    }
}
