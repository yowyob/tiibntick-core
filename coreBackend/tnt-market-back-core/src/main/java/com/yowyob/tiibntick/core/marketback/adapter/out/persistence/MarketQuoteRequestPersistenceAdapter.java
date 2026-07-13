package com.yowyob.tiibntick.core.marketback.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.entity.QuoteRequestEntity;
import com.yowyob.tiibntick.core.marketback.adapter.out.persistence.repository.R2dbcQuoteRequestRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IQuoteRequestRepository;
import com.yowyob.tiibntick.core.marketback.domain.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Persistence adapter for {@link IQuoteRequestRepository} (hexagonal outbound port).
 *
 * <p>Ported from {@code tiibntick-market-backend}'s
 * {@code adapter.outbound.persistence.adapter.QuoteRequestRepositoryAdapter}.
 * That repo had no separate mapper class for this aggregate — entity&lt;-&gt;domain
 * mapping was inlined in the adapter — so the same inline-mapping strategy is
 * kept here, mirroring {@code MarketListingPersistenceAdapter} in this same
 * package.</p>
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketQuoteRequestPersistenceAdapter implements IQuoteRequestRepository {

    private final R2dbcQuoteRequestRepository r2dbcRepo;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<QuoteRequest> save(QuoteRequest request) {
        QuoteRequestEntity entity = toEntity(request);
        return r2dbcRepo.existsById(entity.getId())
                .flatMap(exists -> {
                    entity.setNew(!exists);
                    return r2dbcRepo.save(entity);
                })
                .map(this::toDomain);
    }

    @Override
    public Mono<QuoteRequest> findById(QuoteRequestId id, String tenantId) {
        return r2dbcRepo.findByIdAndTenantId(id.value(), tenantId).map(this::toDomain);
    }

    @Override
    public Flux<QuoteRequest> findByClientId(UUID clientId, String tenantId) {
        return r2dbcRepo.findByClientIdAndTenantId(clientId, tenantId).map(this::toDomain);
    }

    @Override
    public Flux<QuoteRequest> findByProviderId(UUID providerId, String tenantId) {
        return r2dbcRepo.findByProviderIdAndTenantId(providerId, tenantId).map(this::toDomain);
    }

    @Override
    public Flux<QuoteRequest> findByListingId(MarketListingId listingId) {
        return r2dbcRepo.findByListingId(listingId.value()).map(this::toDomain);
    }

    @Override
    public Flux<QuoteRequest> findPendingExpired() {
        return r2dbcRepo.findByStatusAndExpiresAtBefore(QuoteStatus.PENDING.name(), LocalDateTime.now())
                .map(this::toDomain);
    }

    @Override
    public Mono<Void> delete(QuoteRequestId id) {
        return r2dbcRepo.deleteById(id.value());
    }

    // -------------------------------------------------------
    // Entity <-> Domain mapping
    // -------------------------------------------------------

    private QuoteRequestEntity toEntity(QuoteRequest q) {
        DeliveryRequest dr = q.getDeliveryRequest();
        Address pickup = dr != null ? dr.pickupAddress() : null;
        Address delivery = dr != null ? dr.deliveryAddress() : null;
        ParcelSpec parcel = dr != null ? dr.parcelSpec() : null;

        return QuoteRequestEntity.builder()
                .id(q.getId().value())
                .tenantId(q.getTenantId())
                .clientId(q.getClientId())
                .listingId(q.getListingId() != null ? q.getListingId().value() : null)
                .providerId(q.getProviderId())
                .status(q.getStatus().name())
                .notes(q.getNotes())
                .cancellationReason(q.getCancellationReason())
                .selectedResponseId(q.getSelectedResponseId() != null ? q.getSelectedResponseId().value() : null)
                .expiresAt(q.getExpiresAt())
                .pickupStreet(pickup != null ? pickup.street() : null)
                .pickupDistrict(pickup != null ? pickup.district() : null)
                .pickupCity(pickup != null ? pickup.city() : null)
                .pickupLat(pickup != null ? pickup.lat() : null)
                .pickupLng(pickup != null ? pickup.lng() : null)
                .deliveryStreet(delivery != null ? delivery.street() : null)
                .deliveryDistrict(delivery != null ? delivery.district() : null)
                .deliveryCity(delivery != null ? delivery.city() : null)
                .deliveryLat(delivery != null ? delivery.lat() : null)
                .deliveryLng(delivery != null ? delivery.lng() : null)
                .parcelDescription(parcel != null ? parcel.description() : null)
                .weightKg(parcel != null ? parcel.weightKg() : 0)
                .lengthCm(parcel != null ? parcel.lengthCm() : 0)
                .widthCm(parcel != null ? parcel.widthCm() : 0)
                .heightCm(parcel != null ? parcel.heightCm() : 0)
                .valueXaf(parcel != null ? parcel.valueXaf() : 0)
                .fragile(parcel != null && parcel.fragile())
                .perishable(parcel != null && parcel.perishable())
                .requiresInsurance(parcel != null && parcel.requiresInsurance())
                .quantity(parcel != null ? parcel.quantity() : 0)
                .desiredPickupAt(dr != null ? dr.desiredPickupAt() : null)
                .desiredDeliveryAt(dr != null ? dr.desiredDeliveryAt() : null)
                .urgency(dr != null && dr.urgency() != null ? dr.urgency().name() : null)
                .specialInstructions(dr != null ? dr.specialInstructions() : null)
                .responsesJson(toResponsesJson(q.getResponses()))
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .build();
    }

    private QuoteRequest toDomain(QuoteRequestEntity e) {
        Address pickup = new Address(e.getPickupStreet(), e.getPickupDistrict(), e.getPickupCity(),
                null, null, e.getPickupLat(), e.getPickupLng(), null);
        Address delivery = new Address(e.getDeliveryStreet(), e.getDeliveryDistrict(), e.getDeliveryCity(),
                null, null, e.getDeliveryLat(), e.getDeliveryLng(), null);
        ParcelSpec parcel = new ParcelSpec(e.getParcelDescription(), e.getWeightKg(),
                e.getLengthCm(), e.getWidthCm(), e.getHeightCm(), e.getValueXaf(),
                e.isFragile(), e.isPerishable(), e.isRequiresInsurance(), e.getQuantity());
        DeliveryRequest dr = new DeliveryRequest(pickup, delivery, parcel,
                e.getDesiredPickupAt(), e.getDesiredDeliveryAt(),
                e.getUrgency() != null ? DeliveryUrgency.valueOf(e.getUrgency()) : null,
                e.getSpecialInstructions());

        // TODO(market-migration): domain.model.QuoteResponse has no public reconstitute
        // factory (unlike QuoteRequest.reconstitute), so persisted responses cannot be
        // rehydrated into real domain objects here without resorting to reflection.
        // responses_json is written on every save() (see toResponsesJson) as a durable
        // snapshot for audit purposes, but it is not read back into the aggregate yet —
        // this faithfully matches the original QuoteRequestRepositoryAdapter.toDomain(),
        // which also always reconstituted with an empty list ("responses loaded
        // separately", never actually implemented). Once QuoteResponse gains a
        // reconstitute(...) factory in the domain layer, replace the empty list below
        // with a proper deserialization of e.getResponsesJson().
        List<QuoteResponse> responses = Collections.emptyList();

        return QuoteRequest.reconstitute(
                QuoteRequestId.of(e.getId()), e.getTenantId(), e.getClientId(),
                e.getListingId() != null ? MarketListingId.of(e.getListingId()) : null,
                e.getProviderId(), QuoteStatus.valueOf(e.getStatus()), dr,
                responses,
                e.getSelectedResponseId() != null ? QuoteResponseId.of(e.getSelectedResponseId()) : null,
                e.getExpiresAt(), e.getNotes(), e.getCancellationReason(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    /** JSON snapshot record for {@code responses_json} — adapter-local, not a domain type. */
    private record QuoteResponseSnapshot(
            UUID id, UUID providerId, long proposedPriceAmount, String proposedPriceCurrency,
            LocalDateTime estimatedPickupAt, LocalDateTime estimatedDeliveryAt,
            double etaHours, String message, List<String> conditions,
            LocalDateTime validUntil, String status, LocalDateTime createdAt) {
    }

    private String toResponsesJson(List<QuoteResponse> responses) {
        try {
            List<QuoteResponseSnapshot> snapshot = responses.stream()
                    .map(r -> new QuoteResponseSnapshot(
                            r.getId().value(), r.getProviderId(),
                            r.getProposedPrice() != null ? r.getProposedPrice().amount() : 0L,
                            r.getProposedPrice() != null ? r.getProposedPrice().currency() : null,
                            r.getEstimatedPickupAt(), r.getEstimatedDeliveryAt(),
                            r.getEtaHours(), r.getMessage(), r.getConditions(),
                            r.getValidUntil(), r.getStatus() != null ? r.getStatus().name() : null,
                            r.getCreatedAt()))
                    .toList();
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            log.warn("Failed to serialize QuoteRequest responses to JSON, storing null", ex);
            return null;
        }
    }
}
