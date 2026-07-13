package com.yowyob.tiibntick.core.marketback.application.service;

import com.yowyob.tiibntick.core.billing.dsl.domain.model.DeliveryPriority;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PackageType;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import com.yowyob.tiibntick.core.billing.pricing.domain.model.PriceEvaluation;
import com.yowyob.tiibntick.core.billing.pricing.domain.port.in.IPricingUseCase;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.marketback.application.port.in.IManageQuoteRequestUseCase;
import com.yowyob.tiibntick.core.marketback.application.port.in.command.*;
import com.yowyob.tiibntick.core.marketback.application.port.in.result.*;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketEventPublisher;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketNotificationPort;
import com.yowyob.tiibntick.core.marketback.application.port.out.IQuoteRequestRepository;
import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;
import com.yowyob.tiibntick.core.marketback.domain.exception.QuoteRequestNotFoundException;
import com.yowyob.tiibntick.core.marketback.domain.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Application service — manages the QuoteRequest lifecycle: a client submits a
 * request to a provider ({@link #createQuoteRequest}), the provider replies
 * with one or more offers ({@link #submitQuoteResponse}), the client picks one
 * ({@link #selectQuoteResponse}), which is later converted into a MarketOrder
 * by a different vertical.
 *
 * <p>Ported from {@code tiibntick-market-backend}'s
 * {@code application.service.QuoteRequestService}, preserving the original
 * state-transition logic verbatim (state transitions themselves live in the
 * {@link QuoteRequest} aggregate, untouched by this port).</p>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteRequestApplicationService implements IManageQuoteRequestUseCase {

    private static final int DEFAULT_EXPIRY_HOURS = 48;

    private final IQuoteRequestRepository quoteRepository;
    private final IMarketEventPublisher eventPublisher;
    private final IMarketNotificationPort notificationPort;
    private final IPricingUseCase pricingUseCase;

    @Override
    public Mono<QuoteRequestResponse> createQuoteRequest(CreateQuoteRequestCommand cmd) {
        log.debug("Creating QuoteRequest from client={} tenant={}", cmd.clientId(), cmd.tenantId());
        ParcelSpec parcel = new ParcelSpec(cmd.parcelDescription(), cmd.weightKg(),
                cmd.lengthCm(), cmd.widthCm(), cmd.heightCm(), cmd.valueXaf(),
                cmd.fragile(), cmd.perishable(), cmd.requiresInsurance(), cmd.quantity());
        Address pickup = new Address(cmd.pickupStreet(), cmd.pickupDistrict(), cmd.pickupCity(),
                null, null, cmd.pickupLat(), cmd.pickupLng(), null);
        Address delivery = new Address(cmd.deliveryStreet(), cmd.deliveryDistrict(), cmd.deliveryCity(),
                null, null, cmd.deliveryLat(), cmd.deliveryLng(), null);
        DeliveryRequest deliveryReq = new DeliveryRequest(pickup, delivery, parcel,
                cmd.desiredPickupAt(), cmd.desiredDeliveryAt(), cmd.urgency(), cmd.specialInstructions());

        QuoteRequest quote = QuoteRequest.create(cmd.tenantId(), cmd.clientId(),
                MarketListingId.of(cmd.listingId()), cmd.providerId(), deliveryReq, DEFAULT_EXPIRY_HOURS);
        quote.setNotes(cmd.notes());

        return quoteRepository.save(quote)
                .flatMap(saved -> eventPublisher.publishAll(saved.pullDomainEvents())
                        .then(notificationPort.notifyProviderNewQuoteRequest(
                                cmd.tenantId(), saved.getProviderId(), saved.getId().toString(), cmd.pickupCity()))
                        .thenReturn(saved))
                .map(this::toResponse);
    }

    @Override
    public Mono<QuoteRequestResponse> submitQuoteResponse(UUID quoteRequestId, SubmitQuoteResponseCommand cmd, String tenantId) {
        return quoteRepository.findById(QuoteRequestId.of(quoteRequestId), tenantId)
                .switchIfEmpty(Mono.error(new QuoteRequestNotFoundException(quoteRequestId.toString())))
                .flatMap(quote -> resolveProposedPrice(cmd, quote.getDeliveryRequest(), tenantId)
                        .flatMap(price -> {
                            QuoteResponse response = QuoteResponse.create(
                                    cmd.providerId(), price, cmd.estimatedPickupAt(), cmd.estimatedDeliveryAt(),
                                    cmd.etaHours(), cmd.message(), cmd.conditions(), cmd.validHours());
                            quote.addResponse(response);
                            return quoteRepository.save(quote);
                        }))
                .flatMap(saved -> eventPublisher.publishAll(saved.pullDomainEvents()).thenReturn(saved))
                .map(this::toResponse);
    }

    /**
     * Resolves the price to attach to a provider's {@link QuoteResponse}.
     *
     * <p>A provider-submitted price is a manual business decision and is always honored as-is
     * (never overridden by the pricing engine). Only when the provider leaves the price unset
     * ({@code proposedPriceXaf <= 0}, i.e. "use the system's suggested price") do we call the
     * real pricing engine (tnt-billing-pricing → tnt-billing-dsl) against the request's tenant
     * default policy. Falls back to the original (pre-migration) behavior — the submitted price
     * as-is — whenever billing-pricing is unavailable, errors out, or has no matching policy.</p>
     */
    private Mono<Money> resolveProposedPrice(SubmitQuoteResponseCommand cmd, DeliveryRequest request, String tenantId) {
        Money submitted = Money.ofXaf(cmd.proposedPriceXaf());
        if (cmd.proposedPriceXaf() > 0) {
            return Mono.just(submitted);
        }
        UUID tenantUuid = parseTenantIdOrNull(tenantId);
        if (tenantUuid == null || request == null) {
            log.debug("No parsable tenant UUID ('{}') or no delivery request, keeping submitted quote price as-is", tenantId);
            return Mono.just(submitted);
        }
        PricingContext context = toPricingContext(request, tenantUuid);
        return pricingUseCase.evaluateDefaultPolicy(tenantUuid, context)
                .map(evaluation -> {
                    Money converted = toMarketMoney(evaluation);
                    if (converted == null) {
                        log.debug("No pricing policy matched for tenant {}, keeping submitted quote price as-is", tenantUuid);
                        return submitted;
                    }
                    return converted;
                })
                .onErrorResume(ex -> {
                    log.warn("billing-pricing evaluateDefaultPolicy failed for tenant {}, keeping submitted quote price as-is: {}",
                            tenantUuid, ex.toString());
                    return Mono.just(submitted);
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
                .distanceKm(resolveDistanceKm(request))
                .packageTypes(packageTypes)
                .priority(toDeliveryPriority(request.urgency()))
                .tenantId(tenantId)
                .declaredValue(parcel != null ? BigDecimal.valueOf(parcel.valueXaf()) : null)
                .dayOfWeek(LocalDate.now().getDayOfWeek())
                .build();
    }

    /**
     * Distance between pickup and delivery, in km. Prefers tnt-geo-core's
     * {@link GeoPoint#haversineDistanceTo} when both addresses carry coordinates; falls back to
     * {@link DeliveryRequest#distanceKm()} (which itself returns 0.0 when coordinates are
     * missing) otherwise.
     */
    private double resolveDistanceKm(DeliveryRequest request) {
        Address pickup = request.pickupAddress();
        Address delivery = request.deliveryAddress();
        if (pickup != null && delivery != null && pickup.hasCoordinates() && delivery.hasCoordinates()) {
            GeoPoint from = GeoPoint.of(pickup.lat(), pickup.lng());
            GeoPoint to = GeoPoint.of(delivery.lat(), delivery.lng());
            return from.haversineDistanceTo(to);
        }
        return request.distanceKm();
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

    @Override
    public Mono<QuoteRequestResponse> selectQuoteResponse(UUID quoteRequestId, UUID responseId, UUID clientId, String tenantId) {
        return quoteRepository.findById(QuoteRequestId.of(quoteRequestId), tenantId)
                .switchIfEmpty(Mono.error(new QuoteRequestNotFoundException(quoteRequestId.toString())))
                .flatMap(quote -> {
                    quote.selectResponse(QuoteResponseId.of(responseId));
                    return quoteRepository.save(quote);
                })
                .map(this::toResponse);
    }

    /**
     * Rejects a single provider {@link QuoteResponse} within the QuoteRequest, without cancelling
     * the request itself — mirrors {@link #selectQuoteResponse}'s shape (same repository,
     * whole-aggregate persist, no domain event published for this single-response mutation, same
     * as {@code selectResponse} on this aggregate).
     */
    @Override
    public Mono<QuoteRequestResponse> rejectQuoteResponse(UUID quoteRequestId, UUID responseId, UUID clientId, String tenantId) {
        return quoteRepository.findById(QuoteRequestId.of(quoteRequestId), tenantId)
                .switchIfEmpty(Mono.error(new QuoteRequestNotFoundException(quoteRequestId.toString())))
                .flatMap(quote -> {
                    QuoteResponse response = quote.getResponses().stream()
                            .filter(r -> r.getId().equals(QuoteResponseId.of(responseId)))
                            .findFirst()
                            .orElseThrow(() -> new MarketDomainException("Response not found: " + responseId));
                    response.reject();
                    return quoteRepository.save(quote);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<QuoteRequestResponse> cancelQuoteRequest(UUID quoteRequestId, String reason, UUID clientId, String tenantId) {
        return quoteRepository.findById(QuoteRequestId.of(quoteRequestId), tenantId)
                .switchIfEmpty(Mono.error(new QuoteRequestNotFoundException(quoteRequestId.toString())))
                .flatMap(quote -> {
                    quote.cancel(reason);
                    return quoteRepository.save(quote);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<QuoteRequestResponse> getQuoteRequest(UUID quoteRequestId, String tenantId) {
        return quoteRepository.findById(QuoteRequestId.of(quoteRequestId), tenantId)
                .switchIfEmpty(Mono.error(new QuoteRequestNotFoundException(quoteRequestId.toString())))
                .map(this::toResponse);
    }

    @Override
    public Flux<QuoteRequestResponse> getClientQuoteRequests(UUID clientId, String tenantId) {
        return quoteRepository.findByClientId(clientId, tenantId)
                .map(this::toResponse);
    }

    @Override
    public Flux<QuoteRequestResponse> getProviderLeads(UUID providerId, String tenantId) {
        return quoteRepository.findByProviderId(providerId, tenantId)
                .map(this::toResponse);
    }

    private QuoteRequestResponse toResponse(QuoteRequest q) {
        DeliveryRequest dr = q.getDeliveryRequest();
        List<QuoteRequestResponse.QuoteResponseDto> responses = q.getResponses().stream()
                .map(r -> new QuoteRequestResponse.QuoteResponseDto(
                        r.getId().value(), r.getProviderId(),
                        r.getProposedPrice() != null ? r.getProposedPrice().amount() : 0L,
                        r.getEtaHours(), r.getMessage(), r.getValidUntil(),
                        r.getStatus().name(), r.getCreatedAt()))
                .toList();
        return new QuoteRequestResponse(
                q.getId().value(), q.getTenantId(), q.getClientId(),
                q.getListingId() != null ? q.getListingId().value() : null,
                q.getProviderId(), q.getStatus(),
                dr != null && dr.pickupAddress() != null ? dr.pickupAddress().city() : null,
                dr != null && dr.deliveryAddress() != null ? dr.deliveryAddress().city() : null,
                dr != null && dr.parcelSpec() != null ? dr.parcelSpec().weightKg() : 0,
                dr != null ? dr.distanceKm() : 0,
                dr != null ? dr.urgency() : null,
                q.getNotes(), q.getExpiresAt(), responses,
                q.getSelectedResponseId() != null ? q.getSelectedResponseId().value() : null,
                q.getCreatedAt(), q.getUpdatedAt());
    }
}
