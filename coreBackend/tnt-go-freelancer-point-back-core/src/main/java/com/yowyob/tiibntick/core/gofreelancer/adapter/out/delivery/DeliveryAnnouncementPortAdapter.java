package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryAnnouncementUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.RespondToAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.SelectAnnouncementResponseCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementPricingMode;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.RespondToAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The single adapter that talks to {@code tnt-delivery-core} on behalf of gofp's announcement
 * use cases, implementing {@link IDeliveryAnnouncementPort}.
 *
 * <p>Every {@code tnt-delivery-core} type (its {@code DeliveryAnnouncement} aggregate,
 * {@code AnnouncementResponse} entity, commands, value objects) is confined to this class —
 * see {@code architecture/decisions.md} ADR-021. If {@code tnt-delivery-core} changes the
 * shape of its announcement aggregate, only this adapter needs to change.
 *
 * @author MANFOUO BRAUN
 */
@Component
@RequiredArgsConstructor
public class DeliveryAnnouncementPortAdapter implements IDeliveryAnnouncementPort {

    private final DeliveryAnnouncementUseCase deliveryAnnouncementUseCase;
    private final DeliveryQueryUseCase deliveryQueryUseCase;

    @Override
    public Mono<AnnouncementSnapshot> publish(PublishAnnouncementPortCommand command) {
        return deliveryAnnouncementUseCase.publishAnnouncement(toCreateCommand(command))
                .map(this::toSnapshot);
    }

    @Override
    public Mono<Void> cancel(UUID tenantId, UUID announcementId, UUID clientId) {
        return deliveryAnnouncementUseCase.cancelAnnouncement(tenantId, announcementId, clientId);
    }

    @Override
    public Mono<AnnouncementSnapshot> respond(RespondToAnnouncementPortCommand command) {
        return deliveryAnnouncementUseCase.respondToAnnouncement(new RespondToAnnouncementCommand(
                        command.tenantId(),
                        command.announcementId(),
                        command.freelancerId(),
                        command.estimatedArrivalTime(),
                        command.note(),
                        command.proposedPrice(),
                        command.proposedCurrency()))
                .map(this::toSnapshot);
    }

    @Override
    public Mono<AnnouncementSnapshot> selectResponse(
            UUID tenantId, UUID announcementId, UUID clientId, UUID responseId) {
        return deliveryAnnouncementUseCase
                .selectResponse(new SelectAnnouncementResponseCommand(
                        tenantId, announcementId, clientId, responseId))
                .map(this::toSnapshot);
    }

    @Override
    public Mono<BigDecimal> resolveEscrowAmount(UUID tenantId, UUID announcementId, UUID responseId) {
        return deliveryQueryUseCase.findAnnouncementById(tenantId, announcementId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Announcement not found: " + announcementId)))
                .map(a -> {
                    AnnouncementResponse selected = a.getResponses().stream()
                            .filter(r -> r.getId().equals(responseId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Response not found: " + responseId));
                    return a.resolveEscrowAmount(selected);
                });
    }

    @Override
    public Flux<AnnouncementSnapshot> findOpenAnnouncements(UUID tenantId) {
        return deliveryQueryUseCase.findOpenAnnouncements(tenantId).map(this::toSnapshot);
    }

    @Override
    public Mono<AnnouncementSnapshot> findById(UUID tenantId, UUID announcementId) {
        return deliveryQueryUseCase.findAnnouncementById(tenantId, announcementId).map(this::toSnapshot);
    }

    @Override
    public Flux<AnnouncementSnapshot> findByClient(UUID tenantId, UUID clientId) {
        return deliveryQueryUseCase.findAnnouncementsByClient(tenantId, clientId).map(this::toSnapshot);
    }

    // ── gofp command -> tnt-delivery-core command ───────────────────────

    private CreateDeliveryAnnouncementCommand toCreateCommand(PublishAnnouncementPortCommand cmd) {
        AnnouncementPricingMode mode = parsePricingMode(cmd.pricingMode());
        PackageSpecification spec = new PackageSpecification(
                cmd.packageWeightKg(), cmd.packageWidthCm(), cmd.packageHeightCm(), cmd.packageLengthCm(),
                cmd.fragile(), cmd.perishable(), cmd.packageDescription());
        return new CreateDeliveryAnnouncementCommand(
                cmd.tenantId(),
                cmd.clientId(),
                cmd.title(),
                cmd.description(),
                cmd.offeredAmount(),
                cmd.currency(),
                mode,
                spec,
                toDeliveryAddress(cmd.pickupAddress(), "Pickup"),
                toDeliveryAddress(cmd.deliveryAddress(), "Delivery"),
                RecipientInfo.of(cmd.recipientName(), cmd.recipientPhone()),
                DeliveryUrgency.STANDARD);
    }

    private static AnnouncementPricingMode parsePricingMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return AnnouncementPricingMode.FIXED_PRICE;
        }
        try {
            return AnnouncementPricingMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return AnnouncementPricingMode.FIXED_PRICE;
        }
    }

    private static DeliveryAddress toDeliveryAddress(Address a, String fallbackLandmark) {
        if (a == null) {
            return new DeliveryAddress(null, fallbackLandmark, "Unknown", "Yaoundé", "CM", null);
        }
        GeoCoordinates coords = a.getCoordinates()
                .map(c -> new GeoCoordinates(c.getLatitude(), c.getLongitude()))
                .orElse(null);
        return new DeliveryAddress(
                a.getStreet().orElse(null),
                a.getLandmark().orElse(fallbackLandmark),
                a.getQuarter().orElse("Unknown"),
                a.getCity(),
                a.getCountry(),
                coords);
    }

    // ── tnt-delivery-core aggregate -> gofp snapshot ────────────────────

    private AnnouncementSnapshot toSnapshot(DeliveryAnnouncement a) {
        double volumetricWeightDm3 = a.getParcel() != null && a.getParcel().getSpecification() != null
                ? a.getParcel().getSpecification().volumetricWeightDm3()
                : 0.0;
        List<AnnouncementResponseSnapshot> responses = a.getResponses().stream()
                .map(this::toResponseSnapshot)
                .toList();
        return new AnnouncementSnapshot(
                a.getId(),
                a.getTenantId(),
                a.getClientId(),
                a.getTitle(),
                a.getDescription(),
                mapStatus(a.getStatus()),
                a.getCreatedAt(),
                a.getUpdatedAt(),
                a.getOfferedAmount(),
                a.getCurrency(),
                a.getPricingMode() != null ? a.getPricingMode().name() : AnnouncementSnapshot.PRICING_MODE_FIXED_PRICE,
                toCommonAddress(a.getPickupAddress(), "Pickup"),
                toCommonAddress(a.getDeliveryAddress(), "Delivery"),
                volumetricWeightDm3,
                a.getRecipient() != null ? a.getRecipient().name() : null,
                a.getRecipient() != null ? a.getRecipient().phoneNumber() : null,
                a.getSelectedResponseId(),
                responses);
    }

    private AnnouncementResponseSnapshot toResponseSnapshot(AnnouncementResponse r) {
        return new AnnouncementResponseSnapshot(
                r.getId(),
                r.getDeliveryPersonId(),
                r.getProposedPrice(),
                r.getProposedCurrency(),
                r.getStatus() != null ? r.getStatus().name() : null,
                r.getCreatedAt());
    }

    private static Address toCommonAddress(DeliveryAddress a, String fallbackLandmark) {
        if (a == null) {
            return null;
        }
        String street = a.street();
        String landmark = a.landmark();
        if ((street == null || street.isBlank()) && (landmark == null || landmark.isBlank())) {
            landmark = fallbackLandmark;
        }
        Address.Builder builder = Address.builder()
                .street(street)
                .landmark(landmark)
                .quarter(a.district())
                .city(a.city() != null ? a.city() : "Unknown")
                .country(a.country() != null ? a.country() : "CM");
        if (a.coordinates() != null) {
            builder.coordinates(com.yowyob.tiibntick.common.vo.GeoCoordinates.of(
                    a.coordinates().latitude(), a.coordinates().longitude()));
        }
        return builder.build();
    }

    private static AnnouncementStatus mapStatus(
            com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus status) {
        if (status == null) {
            return null;
        }
        try {
            return AnnouncementStatus.valueOf(status.name());
        } catch (IllegalArgumentException e) {
            return AnnouncementStatus.PUBLISHED;
        }
    }
}
