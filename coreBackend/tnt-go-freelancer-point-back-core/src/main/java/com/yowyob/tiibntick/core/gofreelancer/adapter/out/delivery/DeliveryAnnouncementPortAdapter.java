package com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.delivery.application.port.in.AnnouncementLookupUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryAnnouncementUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.CreateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.RespondToAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.SelectAnnouncementResponseCommand;
import com.yowyob.tiibntick.core.delivery.application.port.in.command.UpdateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.entity.AnnouncementResponse;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementPricingMode;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.AnnouncementDetails;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.SenderInfo;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementExtras;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.RespondToAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.UpdateAnnouncementPortCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
    private final AnnouncementLookupUseCase announcementLookupUseCase;

    /** Optional (setter): without it a command naming a parcel is published with a parcel of its own. */
    private com.yowyob.tiibntick.core.delivery.application.port.in.ParcelUseCase parcels;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setParcels(com.yowyob.tiibntick.core.delivery.application.port.in.ParcelUseCase parcels) {
        this.parcels = parcels;
    }

    @Override
    public Mono<AnnouncementSnapshot> publish(PublishAnnouncementPortCommand command) {
        // The goods registered beforehand keep their id: the announcement carries that very parcel.
        Mono<java.util.Optional<com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel>> existing =
                command.parcelId() == null || parcels == null
                        ? Mono.just(java.util.Optional.empty())
                        : parcels.findParcelById(command.parcelId()).map(java.util.Optional::of)
                                .defaultIfEmpty(java.util.Optional.empty());
        return existing
                .flatMap(parcel -> deliveryAnnouncementUseCase.publishAnnouncement(
                        toCreateCommand(command, parcel.orElse(null))))
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
        return selectResponse(tenantId, announcementId, clientId, responseId, null);
    }

    @Override
    public Mono<AnnouncementSnapshot> selectResponse(
            UUID tenantId, UUID announcementId, UUID clientId, UUID responseId, UUID deliveryId) {
        return deliveryAnnouncementUseCase
                .selectResponse(new SelectAnnouncementResponseCommand(
                        tenantId, announcementId, clientId, responseId, deliveryId))
                .map(this::toSnapshot)
                .flatMap(snap -> enrichTracking(tenantId, snap));
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
    public Flux<AnnouncementSnapshot> findOpenAnnouncementsNear(UUID tenantId,
            com.yowyob.tiibntick.core.gofreelancer.application.port.in.query.NearbyCriteria criteria) {
        var query = new com.yowyob.tiibntick.core.delivery.application.port.in.query.NearbyAnnouncementQuery(
                criteria.latitude(), criteria.longitude(), criteria.radiusKm(), criteria.city());
        return deliveryQueryUseCase.findOpenAnnouncementsNear(tenantId, query).map(this::toSnapshot);
    }

    @Override
    public Mono<AnnouncementSnapshot> findById(UUID tenantId, UUID announcementId) {
        return deliveryQueryUseCase.findAnnouncementById(tenantId, announcementId)
                .map(this::toSnapshot)
                .flatMap(snap -> enrichTracking(tenantId, snap));
    }

    @Override
    public Flux<AnnouncementSnapshot> findByClient(UUID tenantId, UUID clientId) {
        return deliveryQueryUseCase.findAnnouncementsByClient(tenantId, clientId).map(this::toSnapshot);
    }

    @Override
    public Mono<AnnouncementSnapshot> update(UpdateAnnouncementPortCommand command) {
        AnnouncementExtras x = command.extras();
        return deliveryAnnouncementUseCase.updateAnnouncement(new UpdateDeliveryAnnouncementCommand(
                        command.tenantId(), command.announcementId(), command.clientId(),
                        command.offeredAmount(), command.currency(),
                        toSender(x), toDetails(x)))
                .map(this::toSnapshot);
    }

    @Override
    public Mono<AnnouncementSnapshot> findByOrigin(UUID tenantId, String originType, UUID originId) {
        return announcementLookupUseCase.findAnnouncementByOrigin(tenantId, originType, originId)
                .map(this::toSnapshot);
    }

    @Override
    public Flux<AnnouncementSnapshot> findByOrigins(UUID tenantId, String originType, Collection<UUID> originIds) {
        return announcementLookupUseCase.findAnnouncementsByOrigins(tenantId, originType, originIds)
                .map(this::toSnapshot);
    }

    @Override
    public Flux<AnnouncementSnapshot> findRespondedBy(UUID tenantId, Collection<UUID> deliveryPersonIds) {
        return announcementLookupUseCase.findAnnouncementsRespondedBy(tenantId, deliveryPersonIds)
                .map(this::toSnapshot);
    }

    @Override
    public Mono<Boolean> hasResponse(UUID announcementId, UUID freelancerId) {
        return deliveryQueryUseCase.hasFreelancerResponded(announcementId, freelancerId);
    }

    @Override
    public Mono<UUID> tenantOf(UUID announcementId) {
        return deliveryQueryUseCase.findAnnouncementTenantId(announcementId);
    }

    // ── gofp command -> tnt-delivery-core command ───────────────────────

    private CreateDeliveryAnnouncementCommand toCreateCommand(PublishAnnouncementPortCommand cmd,
            com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel existingParcel) {
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
                new RecipientInfo(cmd.recipientName(), cmd.recipientPhone(), null,
                        blankToNull(cmd.extras().recipientEmail())),
                parseUrgency(cmd.extras().urgency()),
                existingParcel,
                toSender(cmd.extras()),
                toDetails(cmd.extras()));
    }

    /**
     * Wire urgency to the core's: absent or blank = {@code STANDARD}; the legacy Go value
     * {@code NORMAL} is tolerated as an alias of {@code STANDARD}; anything else must be a core value.
     */
    static DeliveryUrgency parseUrgency(String raw) {
        if (raw == null || raw.isBlank()) {
            return DeliveryUrgency.STANDARD;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("NORMAL".equals(normalized)) {
            return DeliveryUrgency.STANDARD;
        }
        try {
            return DeliveryUrgency.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown urgency: " + raw
                    + " (expected STANDARD, EXPRESS, SAME_DAY or SCHEDULED)");
        }
    }

    private static SenderInfo toSender(AnnouncementExtras x) {
        SenderInfo s = new SenderInfo(blankToNull(x.senderName()), blankToNull(x.senderPhone()),
                blankToNull(x.senderEmail()));
        return s.isEmpty() ? null : s;
    }

    private static AnnouncementDetails toDetails(AnnouncementExtras x) {
        return new AnnouncementDetails(blankToNull(x.requiredVehicleType()), blankToNull(x.paymentMethod()),
                blankToNull(x.transportMethod()), x.distanceKm(), x.estimatedDurationMin(),
                x.destinationHubId(), blankToNull(x.handoverSignatureRef()), x.serviceFee(),
                blankToNull(x.originRefType()), x.originRefId());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /**
     * Accepts canonical {@code FIXED_PRICE}/{@code QUOTE_REQUEST} plus FE aliases
     * {@code FIXED}/{@code QUOTE} and platform shortcuts {@code GO}/{@code FREELANCER}.
     */
    static AnnouncementPricingMode parsePricingMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return AnnouncementPricingMode.FIXED_PRICE;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FIXED", "FIXED_PRICE", "GO" -> AnnouncementPricingMode.FIXED_PRICE;
            case "QUOTE", "QUOTE_REQUEST", "FREELANCER" -> AnnouncementPricingMode.QUOTE_REQUEST;
            default -> {
                try {
                    yield AnnouncementPricingMode.valueOf(normalized);
                } catch (IllegalArgumentException e) {
                    yield AnnouncementPricingMode.FIXED_PRICE;
                }
            }
        };
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

    private Mono<AnnouncementSnapshot> enrichTracking(UUID tenantId, AnnouncementSnapshot snap) {
        if (snap.createdDeliveryId() == null || snap.trackingCode() != null) {
            return Mono.just(snap);
        }
        return deliveryQueryUseCase.findDeliveryById(tenantId, snap.createdDeliveryId())
                .map(d -> snap.withDeliveryLinkage(snap.createdDeliveryId(), d.getTrackingCode()))
                .defaultIfEmpty(snap)
                .onErrorResume(e -> Mono.just(snap));
    }

    private AnnouncementSnapshot toSnapshot(DeliveryAnnouncement a) {
        PackageSpecification spec = a.getParcel() != null ? a.getParcel().getSpecification() : null;
        double volumetricWeightDm3 = spec != null ? spec.volumetricWeightDm3() : 0.0;
        String photoUrl = a.getParcel() != null ? a.getParcel().getPhotoUrl() : null;
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
                spec != null ? spec.weightKg() : null,
                spec != null ? spec.widthCm() : null,
                spec != null ? spec.heightCm() : null,
                spec != null ? spec.lengthCm() : null,
                spec != null ? spec.fragile() : null,
                spec != null ? spec.perishable() : null,
                spec != null ? spec.description() : null,
                photoUrl,
                a.getRecipient() != null ? a.getRecipient().name() : null,
                a.getRecipient() != null ? a.getRecipient().phoneNumber() : null,
                a.getSelectedResponseId(),
                a.getCreatedDeliveryId(),
                null,
                responses,
                toExtras(a));
    }

    private static AnnouncementExtras toExtras(DeliveryAnnouncement a) {
        SenderInfo sender = a.getSender();
        AnnouncementDetails d = a.getDetails() != null ? a.getDetails() : AnnouncementDetails.EMPTY;
        return new AnnouncementExtras(
                sender != null ? sender.name() : null,
                sender != null ? sender.phoneNumber() : null,
                sender != null ? sender.email() : null,
                a.getRecipient() != null ? a.getRecipient().email() : null,
                a.getUrgency() != null ? a.getUrgency().name() : null,
                d.requiredVehicleType(), d.paymentMethod(), d.transportMethod(), d.distanceKm(),
                d.estimatedDurationMin(), d.destinationHubId(), d.handoverSignatureRef(), d.serviceFee(),
                d.originRefType(), d.originRefId());
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
