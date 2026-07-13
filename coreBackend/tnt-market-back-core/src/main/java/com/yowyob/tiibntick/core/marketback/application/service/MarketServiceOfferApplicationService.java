package com.yowyob.tiibntick.core.marketback.application.service;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.DeliveryPriority;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PackageType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PriceEvaluation;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IPricingUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageServiceOfferUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.CreateServiceOfferCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.SimulatePriceCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.UpdateServiceOfferCommand;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.PriceSimulationResponse;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.ServiceOfferResponse;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketListingRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IServiceOfferRepository;
import com.yowyob.tiibntick.core.marketback.domain.exception.ListingNotFoundException;
import com.yowyob.tiibntick.core.marketback.domain.exception.ServiceOfferNotFoundException;
import com.yowyob.tiibntick.core.marketback.domain.model.Address;
import com.yowyob.tiibntick.core.marketback.domain.model.DeliveryRequest;
import com.yowyob.tiibntick.core.marketback.domain.model.DeliveryUrgency;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import com.yowyob.tiibntick.core.marketback.domain.model.Money;
import com.yowyob.tiibntick.core.marketback.domain.model.OfferAvailability;
import com.yowyob.tiibntick.core.marketback.domain.model.ParcelSpec;
import com.yowyob.tiibntick.core.marketback.domain.model.PricingRules;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceConstraints;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceOffer;
import com.yowyob.tiibntick.core.marketback.domain.model.ServiceOfferId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Application service — manages the full lifecycle of ServiceOffers.
 * Ported from the standalone app's {@code ServiceOfferService}, mechanically
 * adapted to this module's port/domain package layout.
 *
 * <p>NOTE(market-migration): business/tnt-product-core also defines its own
 * "ServiceOffer" concept (GetServiceOfferUseCase/PublishServiceOfferUseCase) —
 * this is a separate aggregate; reconciling the two is a product decision, not
 * attempted here.</p>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketServiceOfferApplicationService implements IManageServiceOfferUseCase {

    private final IServiceOfferRepository offerRepository;
    private final IMarketListingRepository listingRepository;
    private final IPricingUseCase pricingUseCase;

    @Override
    public Mono<ServiceOfferResponse> createOffer(CreateServiceOfferCommand cmd) {
        log.debug("Creating ServiceOffer for listing={} tenant={}", cmd.listingId(), cmd.tenantId());
        return listingRepository.findById(new MarketListingId(cmd.listingId()), cmd.tenantId())
                .switchIfEmpty(Mono.error(new ListingNotFoundException(cmd.listingId().toString())))
                .flatMap(listing -> {
                    PricingRules pricing = new PricingRules(
                            Money.ofXaf(cmd.basePriceXaf()), Money.ofXaf(cmd.perKmRateXaf()),
                            Money.ofXaf(cmd.perKgRateXaf()), Money.ofXaf(cmd.minimumPriceXaf()),
                            Money.ofXaf(cmd.maximumPriceXaf()), "XAF", cmd.pricingDslExpression());
                    ServiceConstraints constraints = new ServiceConstraints(
                            cmd.maxWeightKg(), cmd.maxLengthCm(), cmd.maxWidthCm(), cmd.maxHeightCm(),
                            cmd.maxValueXaf(), cmd.acceptsFragile(), cmd.acceptsPerishable(),
                            false, false, cmd.maxDistanceKm());
                    OfferAvailability availability = new OfferAvailability(
                            cmd.daysOfWeek(),
                            cmd.openTime() != null ? LocalTime.parse(cmd.openTime()) : null,
                            cmd.closeTime() != null ? LocalTime.parse(cmd.closeTime()) : null,
                            List.of(), cmd.expressAvailable(), cmd.sameDayAvailable());

                    ServiceOffer offer = ServiceOffer.create(
                            cmd.tenantId(),
                            listing.getId(),
                            listing.getProviderId(),
                            cmd.name(),
                            cmd.serviceType(),
                            pricing, constraints, availability);
                    offer.setDescription(cmd.description());

                    return offerRepository.save(offer);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<ServiceOfferResponse> updateOffer(UUID offerId, UpdateServiceOfferCommand cmd, String tenantId) {
        return offerRepository.findById(new ServiceOfferId(offerId))
                .switchIfEmpty(Mono.error(new ServiceOfferNotFoundException(offerId.toString())))
                .flatMap(offer -> {
                    if (cmd.basePriceXaf() != null) {
                        PricingRules existing = offer.getPricingRules();
                        PricingRules rules = new PricingRules(
                                Money.ofXaf(cmd.basePriceXaf()),
                                cmd.perKmRateXaf() != null ? Money.ofXaf(cmd.perKmRateXaf()) : existing.perKmRate(),
                                cmd.perKgRateXaf() != null ? Money.ofXaf(cmd.perKgRateXaf()) : existing.perKgRate(),
                                cmd.minimumPriceXaf() != null ? Money.ofXaf(cmd.minimumPriceXaf()) : existing.minimumPrice(),
                                existing.maximumPrice(), existing.currency(),
                                cmd.pricingDslExpression() != null ? cmd.pricingDslExpression() : existing.pricingDslExpression());
                        offer.updatePricing(rules);
                    }
                    if (cmd.maxWeightKg() != null) {
                        ServiceConstraints existing = offer.getServiceConstraints();
                        ServiceConstraints constraints = new ServiceConstraints(
                                cmd.maxWeightKg(), existing.maxLengthCm(), existing.maxWidthCm(), existing.maxHeightCm(),
                                existing.maxValueXaf(),
                                cmd.acceptsFragile() != null ? cmd.acceptsFragile() : existing.acceptsFragile(),
                                cmd.acceptsPerishable() != null ? cmd.acceptsPerishable() : existing.acceptsPerishable(),
                                existing.acceptsHazardous(), existing.requiresInsurance(), existing.maxDistanceKm());
                        offer.updateConstraints(constraints);
                    }
                    if (cmd.daysOfWeek() != null) {
                        OfferAvailability existing = offer.getAvailability();
                        OfferAvailability availability = new OfferAvailability(
                                cmd.daysOfWeek(), existing.openTime(), existing.closeTime(),
                                existing.exceptionalClosures(),
                                cmd.expressAvailable() != null ? cmd.expressAvailable() : existing.expressAvailable(),
                                cmd.sameDayAvailable() != null ? cmd.sameDayAvailable() : existing.sameDayAvailable());
                        offer.updateAvailability(availability);
                    }
                    if (cmd.description() != null) offer.setDescription(cmd.description());
                    // NOTE: cmd.name()/cmd.serviceType() are intentionally not applied — the
                    // ServiceOffer aggregate exposes no setter for either (renaming/retyping
                    // an offer post-creation was not supported in the original service either).
                    return offerRepository.save(offer);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<ServiceOfferResponse> activateOffer(UUID offerId, String tenantId) {
        return offerRepository.findById(new ServiceOfferId(offerId))
                .switchIfEmpty(Mono.error(new ServiceOfferNotFoundException(offerId.toString())))
                .flatMap(offer -> {
                    offer.activate();
                    return offerRepository.save(offer);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<ServiceOfferResponse> deactivateOffer(UUID offerId, String tenantId) {
        return offerRepository.findById(new ServiceOfferId(offerId))
                .switchIfEmpty(Mono.error(new ServiceOfferNotFoundException(offerId.toString())))
                .flatMap(offer -> {
                    offer.deactivate();
                    return offerRepository.save(offer);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<ServiceOfferResponse> archiveOffer(UUID offerId, String tenantId) {
        return offerRepository.findById(new ServiceOfferId(offerId))
                .switchIfEmpty(Mono.error(new ServiceOfferNotFoundException(offerId.toString())))
                .flatMap(offer -> {
                    offer.archive();
                    return offerRepository.save(offer);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<ServiceOfferResponse> getOffer(UUID offerId, String tenantId) {
        return offerRepository.findById(new ServiceOfferId(offerId))
                .switchIfEmpty(Mono.error(new ServiceOfferNotFoundException(offerId.toString())))
                .map(this::toResponse);
    }

    @Override
    public Flux<ServiceOfferResponse> getOffersByListing(UUID listingId, String tenantId) {
        return offerRepository.findByListingId(new MarketListingId(listingId))
                .map(this::toResponse);
    }

    @Override
    public Flux<ServiceOfferResponse> getActiveOffersByListing(UUID listingId, String tenantId) {
        return offerRepository.findActiveByListingId(new MarketListingId(listingId))
                .map(this::toResponse);
    }

    @Override
    public Mono<PriceSimulationResponse> simulatePrice(UUID offerId, SimulatePriceCommand cmd, String tenantId) {
        return offerRepository.findById(new ServiceOfferId(offerId))
                .switchIfEmpty(Mono.error(new ServiceOfferNotFoundException(offerId.toString())))
                .flatMap(offer -> {
                    ParcelSpec parcel = new ParcelSpec(null, cmd.weightKg(), cmd.lengthCm(), cmd.widthCm(),
                            cmd.heightCm(), cmd.valueXaf(), cmd.fragile(), cmd.perishable(), false, 1);
                    Address pickup = new Address(null, null, cmd.pickupCity(), null, null,
                            cmd.pickupLat(), cmd.pickupLng(), null);
                    Address delivery = new Address(null, null, cmd.deliveryCity(), null, null,
                            cmd.deliveryLat(), cmd.deliveryLng(), null);
                    DeliveryRequest request = new DeliveryRequest(pickup, delivery, parcel,
                            null, null, cmd.urgency(), null);
                    boolean available = offer.isAvailableFor(request);
                    Mono<Money> estimateMono = available
                            ? resolveEstimatedPrice(offer, tenantId, request)
                            : Mono.just(Money.zeroXaf());
                    return estimateMono
                            .map(estimated -> {
                                long price = estimated != null ? estimated.amount() : 0L;
                                long basePrice = offer.getPricingRules().basePrice() != null
                                        ? offer.getPricingRules().basePrice().amount() : 0L;
                                return new PriceSimulationResponse(offerId, offer.getName(),
                                        price, basePrice, Math.max(0, price - basePrice), 0L, 0L,
                                        request.distanceKm(), cmd.pickupCity(), cmd.deliveryCity(), null);
                            });
                });
    }

    /**
     * Calls the real pricing engine (tnt-billing-pricing → tnt-billing-dsl) to price the
     * given delivery request against the tenant's default pricing policy. Falls back to the
     * offer's naive {@link PricingRules#estimate} formula whenever billing-pricing is
     * unavailable, errors out, or has no matching policy for the tenant — never fails the
     * simulation because of the enrichment call.
     */
    private Mono<Money> resolveEstimatedPrice(ServiceOffer offer, String tenantId, DeliveryRequest request) {
        UUID tenantUuid = parseTenantIdOrNull(tenantId);
        if (tenantUuid == null) {
            log.debug("No parsable tenant UUID ('{}') for offer {}, using naive pricing formula", tenantId, offer.getId());
            return Mono.just(offer.estimatePrice(request));
        }
        PricingContext context = toPricingContext(request, tenantUuid);
        return pricingUseCase.evaluateDefaultPolicy(tenantUuid, context)
                .map(evaluation -> {
                    Money converted = toMarketMoney(evaluation);
                    if (converted == null) {
                        log.debug("No pricing policy matched for tenant {} offer {}, falling back to naive formula",
                                tenantUuid, offer.getId());
                        return offer.estimatePrice(request);
                    }
                    return converted;
                })
                .onErrorResume(ex -> {
                    log.warn("billing-pricing evaluateDefaultPolicy failed for tenant {} offer {}, falling back to naive formula: {}",
                            tenantUuid, offer.getId(), ex.toString());
                    return Mono.just(offer.estimatePrice(request));
                });
    }

    /** Converts the pricing engine's evaluation into this module's own {@link Money}, or {@code null} if no rule matched. */
    private Money toMarketMoney(PriceEvaluation evaluation) {
        if (evaluation == null || !evaluation.hasBaseRule() || evaluation.getSellingPrice() == null) {
            return null;
        }
        return toMarketMoney(evaluation.getSellingPrice());
    }

    /**
     * Field-by-field conversion of tnt-billing-dsl's {@code Money} (BigDecimal amount +
     * java.util.Currency) into this module's own {@link Money} (long amount + ISO currency code).
     */
    private Money toMarketMoney(com.yowyob.tiibntick.core.billing.dsl.domain.model.Money dslMoney) {
        if (dslMoney == null) return null;
        long amount = dslMoney.getAmount().setScale(0, RoundingMode.HALF_UP).longValue();
        return new Money(amount, dslMoney.getCurrency().getCurrencyCode());
    }

    private PricingContext toPricingContext(DeliveryRequest request, UUID tenantId) {
        ParcelSpec parcel = request.parcelSpec();
        List<PackageType> packageTypes = new ArrayList<>();
        if (parcel != null) {
            if (parcel.fragile()) packageTypes.add(PackageType.FRAGILE);
            if (parcel.perishable()) packageTypes.add(PackageType.PERISHABLE);
        }
        if (packageTypes.isEmpty()) packageTypes.add(PackageType.STANDARD);
        return PricingContext.builder()
                .weightKg(parcel != null ? parcel.weightKg() : 0.0)
                .distanceKm(request.distanceKm())
                .packageTypes(packageTypes)
                .priority(toDeliveryPriority(request.urgency()))
                .tenantId(tenantId)
                .declaredValue(parcel != null ? BigDecimal.valueOf(parcel.valueXaf()) : null)
                .dayOfWeek(LocalDate.now().getDayOfWeek())
                .build();
    }

    private DeliveryPriority toDeliveryPriority(DeliveryUrgency urgency) {
        if (urgency == null) return DeliveryPriority.NORMAL;
        return switch (urgency) {
            case EXPRESS -> DeliveryPriority.HIGH;
            case SAME_DAY -> DeliveryPriority.URGENT;
            case STANDARD -> DeliveryPriority.NORMAL;
        };
    }

    private UUID parseTenantIdOrNull(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) return null;
        try {
            return UUID.fromString(tenantId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ServiceOfferResponse toResponse(ServiceOffer o) {
        PricingRules pricing = o.getPricingRules();
        ServiceConstraints constraints = o.getServiceConstraints();
        OfferAvailability availability = o.getAvailability();
        return new ServiceOfferResponse(
                o.getId().value(), o.getListingId().value(), o.getProviderId(),
                o.getName(), o.getDescription(), o.getServiceType(), o.getStatus(),
                pricing != null && pricing.basePrice() != null ? pricing.basePrice().amount() : 0L,
                pricing != null && pricing.perKmRate() != null ? pricing.perKmRate().amount() : 0L,
                pricing != null && pricing.perKgRate() != null ? pricing.perKgRate().amount() : 0L,
                pricing != null && pricing.minimumPrice() != null ? pricing.minimumPrice().amount() : 0L,
                constraints != null ? constraints.maxWeightKg() : 0,
                constraints != null ? constraints.maxDistanceKm() : 0,
                constraints != null && constraints.acceptsFragile(),
                constraints != null && constraints.acceptsPerishable(),
                availability != null && availability.expressAvailable(),
                availability != null && availability.sameDayAvailable(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
