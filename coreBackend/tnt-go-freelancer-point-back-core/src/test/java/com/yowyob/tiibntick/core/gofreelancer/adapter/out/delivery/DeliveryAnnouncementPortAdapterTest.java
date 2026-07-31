package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryAnnouncementUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementPricingMode;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeliveryAnnouncementPortAdapter} — the single place allowed to touch
 * {@code tnt-delivery-core} types on behalf of gofp (see {@code architecture/decisions.md}
 * ADR-021). Focuses on the two mapping directions that are easy to get subtly wrong: the
 * pricing-mode string round-trip and the {@code common.vo.Address} ↔ {@code DeliveryAddress}
 * conversion (including the fallback-landmark safety net for addresses with neither a street
 * nor a landmark).
 *
 * @author MANFOUO BRAUN
 */
@ExtendWith(MockitoExtension.class)
class DeliveryAnnouncementPortAdapterTest {

    @Mock private DeliveryAnnouncementUseCase deliveryAnnouncementUseCase;
    @Mock private DeliveryQueryUseCase deliveryQueryUseCase;

    private DeliveryAnnouncementPortAdapter adapter;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new DeliveryAnnouncementPortAdapter(deliveryAnnouncementUseCase, deliveryQueryUseCase);
    }

    @Test
    void publish_mapsGofpCommandToDeliveryCoreCommand_forFixedPrice() {
        PublishAnnouncementPortCommand cmd = new PublishAnnouncementPortCommand(
                tenantId, clientId, "My announcement", "desc",
                BigDecimal.valueOf(5000), "XAF", "FIXED_PRICE",
                2.0, 20, 20, 20, false, false, "A box",
                null, null, "Jean Dupont", "+237600000000");

        when(deliveryAnnouncementUseCase.publishAnnouncement(any()))
                .thenReturn(Mono.just(realAnnouncement(AnnouncementPricingMode.FIXED_PRICE, BigDecimal.valueOf(5000))));

        StepVerifier.create(adapter.publish(cmd))
                .assertNext(snapshot -> {
                    assertThat(snapshot.pricingMode()).isEqualTo(AnnouncementSnapshot.PRICING_MODE_FIXED_PRICE);
                    assertThat(snapshot.offeredAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
                })
                .verifyComplete();

        ArgumentCaptor<CreateDeliveryAnnouncementCommand> captor =
                ArgumentCaptor.forClass(CreateDeliveryAnnouncementCommand.class);
        org.mockito.Mockito.verify(deliveryAnnouncementUseCase).publishAnnouncement(captor.capture());
        assertThat(captor.getValue().pricingMode()).isEqualTo(AnnouncementPricingMode.FIXED_PRICE);
        assertThat(captor.getValue().recipient().name()).isEqualTo("Jean Dupont");
    }

    @Test
    void publish_blankPricingMode_defaultsToFixedPrice() {
        PublishAnnouncementPortCommand cmd = new PublishAnnouncementPortCommand(
                tenantId, clientId, "title", "desc",
                BigDecimal.valueOf(1000), "XAF", "",
                1.0, 10, 10, 10, false, false, "pkg",
                null, null, "Jean", "+237600000000");

        when(deliveryAnnouncementUseCase.publishAnnouncement(any()))
                .thenReturn(Mono.just(realAnnouncement(AnnouncementPricingMode.FIXED_PRICE, BigDecimal.valueOf(1000))));

        adapter.publish(cmd).block();

        ArgumentCaptor<CreateDeliveryAnnouncementCommand> captor =
                ArgumentCaptor.forClass(CreateDeliveryAnnouncementCommand.class);
        org.mockito.Mockito.verify(deliveryAnnouncementUseCase).publishAnnouncement(captor.capture());
        assertThat(captor.getValue().pricingMode()).isEqualTo(AnnouncementPricingMode.FIXED_PRICE);
    }

    @Test
    void findById_quoteRequestAnnouncement_mapsPricingModeAndResponsesToSnapshot() {
        DeliveryAnnouncement announcement = realAnnouncement(AnnouncementPricingMode.QUOTE_REQUEST, null);

        when(deliveryQueryUseCase.findAnnouncementById(tenantId, announcement.getId()))
                .thenReturn(Mono.just(announcement));

        StepVerifier.create(adapter.findById(tenantId, announcement.getId()))
                .assertNext(snapshot -> {
                    assertThat(snapshot.pricingMode()).isEqualTo(AnnouncementSnapshot.PRICING_MODE_QUOTE_REQUEST);
                    assertThat(snapshot.id()).isEqualTo(announcement.getId());
                    assertThat(snapshot.tenantId()).isEqualTo(tenantId);
                    assertThat(snapshot.clientId()).isEqualTo(clientId);
                    assertThat(snapshot.pickupAddress().toDisplayString()).contains("Yaoundé");
                })
                .verifyComplete();
    }

    @Test
    void findById_addressWithNoStreetOrLandmark_fallsBackToPlaceholderLandmarkInsteadOfThrowing() {
        DeliveryAddress bareAddress = new DeliveryAddress(null, null, "Bastos", "Yaoundé", "CM", null);
        DeliveryAnnouncement announcement = DeliveryAnnouncement.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .clientId(clientId)
                .title("t")
                .description("d")
                .offeredAmount(BigDecimal.TEN)
                .currency("XAF")
                .pricingMode(AnnouncementPricingMode.FIXED_PRICE)
                .parcel(Parcel.create(new PackageSpecification(1.0, 10, 10, 10, false, false, "pkg")))
                .pickupAddress(bareAddress)
                .deliveryAddress(bareAddress)
                .recipient(RecipientInfo.of("Jean", "+237600000000"))
                .urgency(DeliveryUrgency.STANDARD)
                .status(com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus.PUBLISHED)
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .build();

        when(deliveryQueryUseCase.findAnnouncementById(tenantId, announcement.getId()))
                .thenReturn(Mono.just(announcement));

        // Must not throw Address's "at least one of street/landmark" IllegalArgumentException.
        StepVerifier.create(adapter.findById(tenantId, announcement.getId()))
                .assertNext(snapshot -> assertThat(snapshot.pickupAddress().toDisplayString())
                        .contains("Pickup", "Yaoundé"))
                .verifyComplete();
    }

    private DeliveryAnnouncement realAnnouncement(AnnouncementPricingMode mode, BigDecimal offeredAmount) {
        return DeliveryAnnouncement.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .clientId(clientId)
                .title("My announcement")
                .description("desc")
                .offeredAmount(offeredAmount)
                .currency("XAF")
                .pricingMode(mode)
                .parcel(Parcel.create(new PackageSpecification(2.0, 20, 20, 20, false, false, "A box")))
                .pickupAddress(new DeliveryAddress(null, "Marché", "Mokolo", "Yaoundé", "CM",
                        new GeoCoordinates(3.87, 11.52)))
                .deliveryAddress(new DeliveryAddress(null, "Carrefour", "Bastos", "Yaoundé", "CM", null))
                .recipient(RecipientInfo.of("Jean Dupont", "+237600000000"))
                .urgency(DeliveryUrgency.STANDARD)
                .status(com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus.PUBLISHED)
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .build();
    }
}
