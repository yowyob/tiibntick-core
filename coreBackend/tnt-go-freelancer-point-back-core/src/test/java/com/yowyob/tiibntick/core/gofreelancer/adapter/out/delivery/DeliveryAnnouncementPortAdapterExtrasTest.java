package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.delivery.application.port.in.AnnouncementLookupUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryAnnouncementUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.UpdateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementPricingMode;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementExtras;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.UpdateAnnouncementPortCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot L2 — contract of {@link DeliveryAnnouncementPortAdapter} for the complements that used to live
 * in the gofp mirror: every field goes to delivery-core and comes back (round trip through the real
 * core aggregate), and the urgency wire value is mapped ({@code NORMAL} tolerated).
 */
@DisplayName("Port annonce : compléments aller-retour et urgence")
class DeliveryAnnouncementPortAdapterExtrasTest {

    private final DeliveryAnnouncementUseCase useCase = mock(DeliveryAnnouncementUseCase.class);
    private final AnnouncementLookupUseCase lookup = mock(AnnouncementLookupUseCase.class);
    private DeliveryAnnouncementPortAdapter adapter;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID hub = UUID.randomUUID();
    private final UUID need = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new DeliveryAnnouncementPortAdapter(useCase, mock(DeliveryQueryUseCase.class), lookup);
    }

    private AnnouncementExtras fullExtras(String urgency) {
        return new AnnouncementExtras("Jean Mbarga", "+237690000001", "jean@example.org", "marie@example.org",
                urgency, "MOTORBIKE", "CASH", "ROAD", new BigDecimal("12.500"), 45, hub, "sig://abc",
                new BigDecimal("300.00"), AnnouncementExtras.ORIGIN_DELIVERY_NEED, need);
    }

    private PublishAnnouncementPortCommand command(AnnouncementExtras extras) {
        return new PublishAnnouncementPortCommand(tenantId, clientId, "Colis", "desc", null, "XAF",
                "QUOTE_REQUEST", 2.0, 20, 20, 20, false, false, "A box", null, null,
                "Marie Ngono", "+237691000002", null, extras);
    }

    /** Publishes through the real core factory so the snapshot is read back from a real aggregate. */
    private void publishForReal() {
        when(useCase.publishAnnouncement(any())).thenAnswer(inv -> {
            CreateDeliveryAnnouncementCommand c = inv.getArgument(0);
            DeliveryAnnouncement a = DeliveryAnnouncement.createDraft(c.tenantId(), c.clientId(), c.title(),
                    c.description(), c.offeredAmount(), c.currency(), c.pricingMode(),
                    Parcel.create(c.packageSpec()), addr(), addr(), c.recipient(), c.urgency(), c.sender(),
                    c.details());
            a.publish();
            return Mono.just(a);
        });
    }

    private static DeliveryAddress addr() {
        return new DeliveryAddress("Rue 1", "Carrefour", "Bastos", "Douala", "CM", null);
    }

    @Test
    @DisplayName("tous les compléments vont au core et reviennent à l'identique")
    void publishRoundTrip() {
        publishForReal();
        AnnouncementExtras sent = fullExtras("SAME_DAY");

        StepVerifier.create(adapter.publish(command(sent)))
                .assertNext(snapshot -> {
                    assertThat(snapshot.extras()).isEqualTo(sent);
                    assertThat(snapshot.recipientName()).isEqualTo("Marie Ngono");
                    assertThat(snapshot.pricingMode()).isEqualTo(AnnouncementSnapshot.PRICING_MODE_QUOTE_REQUEST);
                })
                .verifyComplete();

        ArgumentCaptor<CreateDeliveryAnnouncementCommand> core = ArgumentCaptor.forClass(CreateDeliveryAnnouncementCommand.class);
        org.mockito.Mockito.verify(useCase).publishAnnouncement(core.capture());
        assertThat(core.getValue().pricingMode()).as("Freelancer = QUOTE_REQUEST préservé").isEqualTo(AnnouncementPricingMode.QUOTE_REQUEST);
        assertThat(core.getValue().recipient().email()).isEqualTo("marie@example.org");
        assertThat(core.getValue().sender().name()).isEqualTo("Jean Mbarga");
        assertThat(core.getValue().details().originRefId()).isEqualTo(need);
        assertThat(core.getValue().urgency()).isEqualTo(DeliveryUrgency.SAME_DAY);
    }

    @Test
    @DisplayName("sans complément : rien d'inventé, urgence STANDARD")
    void publishWithoutExtras() {
        publishForReal();

        StepVerifier.create(adapter.publish(command(null)))
                .assertNext(snapshot -> {
                    assertThat(snapshot.extras().senderName()).isNull();
                    assertThat(snapshot.extras().paymentMethod()).isNull();
                    assertThat(snapshot.extras().originRefId()).isNull();
                    assertThat(snapshot.extras().urgency()).isEqualTo("STANDARD");
                })
                .verifyComplete();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(value = {"NULL,STANDARD", "'',STANDARD", "NORMAL,STANDARD", "normal,STANDARD", "STANDARD,STANDARD",
            "EXPRESS,EXPRESS", "SAME_DAY,SAME_DAY", "scheduled,SCHEDULED"}, nullValues = "NULL")
    @DisplayName("urgence : valeurs du core acceptées, NORMAL toléré comme STANDARD")
    void urgencyMapping(String wire, DeliveryUrgency expected) {
        assertThat(DeliveryAnnouncementPortAdapter.parseUrgency(wire)).isEqualTo(expected);
    }

    @Test
    @DisplayName("urgence inconnue → refus explicite")
    void unknownUrgencyRefused() {
        assertThatThrownBy(() -> DeliveryAnnouncementPortAdapter.parseUrgency("URGENT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URGENT");
    }

    @Test
    @DisplayName("le DTO d'annonce accepte « urgency » en JSON (optionnel)")
    void requestDtoReadsUrgency() throws Exception {
        ObjectMapper json = new ObjectMapper();
        assertThat(json.readValue("{\"urgency\":\"NORMAL\"}", AnnouncementRequestDTO.class).getUrgency()).isEqualTo("NORMAL");
        assertThat(json.readValue("{}", AnnouncementRequestDTO.class).getUrgency()).isNull();
    }

    @Test
    @DisplayName("mise à jour : montant, devise et compléments partent au core")
    void updateGoesToCore() {
        DeliveryAnnouncement a = DeliveryAnnouncement.createDraft(tenantId, clientId, "Colis", "desc",
                new BigDecimal("2500"), "XAF", AnnouncementPricingMode.FIXED_PRICE,
                Parcel.create(new PackageSpecification(2.0, 20, 20, 20, false, false, "A box")), addr(), addr(),
                RecipientInfo.of("Marie Ngono", "+237691000002"), DeliveryUrgency.STANDARD, null, null);
        when(useCase.updateAnnouncement(any())).thenReturn(Mono.just(a));

        adapter.update(new UpdateAnnouncementPortCommand(tenantId, a.getId(), clientId, new BigDecimal("3000"), "XAF",
                fullExtras(null))).block();

        ArgumentCaptor<UpdateDeliveryAnnouncementCommand> cmd = ArgumentCaptor.forClass(UpdateDeliveryAnnouncementCommand.class);
        org.mockito.Mockito.verify(useCase).updateAnnouncement(cmd.capture());
        assertThat(cmd.getValue().offeredAmount()).isEqualByComparingTo("3000");
        assertThat(cmd.getValue().clientId()).isEqualTo(clientId);
        assertThat(cmd.getValue().details().paymentMethod()).isEqualTo("CASH");
        assertThat(cmd.getValue().sender().email()).isEqualTo("jean@example.org");
    }

    @Test
    @DisplayName("recherche par origine : délègue au core avec le type DELIVERY_NEED")
    void findByOriginDelegates() {
        when(lookup.findAnnouncementByOrigin(tenantId, "DELIVERY_NEED", need)).thenReturn(Mono.empty());
        StepVerifier.create(adapter.findByOrigin(tenantId, AnnouncementExtras.ORIGIN_DELIVERY_NEED, need)).verifyComplete();
        org.mockito.Mockito.verify(lookup).findAnnouncementByOrigin(tenantId, "DELIVERY_NEED", need);
    }
}
