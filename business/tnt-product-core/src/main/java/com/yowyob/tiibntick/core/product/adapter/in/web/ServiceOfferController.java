package com.yowyob.tiibntick.core.product.adapter.in.web;

import com.yowyob.tiibntick.core.product.application.port.in.*;
import com.yowyob.tiibntick.core.product.domain.model.OfferComparison;
import com.yowyob.tiibntick.core.product.domain.model.ServiceOffer;
import com.yowyob.tiibntick.core.product.domain.model.ServiceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for TiiBnTick logistics service offer management.
 * Path aligned with Kernel Core's product-offer convention at {@code /api/service-offers}.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Service Offers", description = "Logistics service offer lifecycle and matching")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ServiceOfferController {

    private final CreateServiceOfferUseCase createOfferUseCase;
    private final GetServiceOfferUseCase getOfferUseCase;
    private final ListServiceOffersByProviderUseCase listOffersUseCase;
    private final PublishServiceOfferUseCase publishOfferUseCase;
    private final FindMatchingOffersUseCase findMatchingUseCase;
    private final CompareOffersUseCase compareOffersUseCase;

    @Operation(summary = "Create a new service offer")
    @PostMapping("/api/service-offers")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ServiceOffer> createOffer(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestBody CreateOfferRequest body) {
        UUID catalogProductId = body.catalogProductId() != null
                ? UUID.fromString(body.catalogProductId()) : null;
        CreateServiceOfferCommand cmd = new CreateServiceOfferCommand(
                tenantId, UUID.fromString(body.providerId()), catalogProductId,
                body.name(), body.description(), ServiceType.valueOf(body.type()),
                body.maxWeightKg(), body.maxDistanceKm(), body.deliveryWindowHours(),
                body.coverageZoneId() != null ? UUID.fromString(body.coverageZoneId()) : null,
                body.policyId());
        return createOfferUseCase.createServiceOffer(cmd);
    }

    @Operation(summary = "Get service offer by ID")
    @GetMapping("/api/service-offers/{offerId}")
    public Mono<ServiceOffer> getOffer(@PathVariable UUID offerId) {
        return getOfferUseCase.getServiceOffer(offerId);
    }

    @Operation(summary = "List service offers by provider (agency/org)")
    @GetMapping("/api/providers/{providerId}/service-offers")
    public Flux<ServiceOffer> listByProvider(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID providerId) {
        return listOffersUseCase.listByProvider(tenantId, providerId);
    }

    @Operation(summary = "Find offers matching cargo weight and distance")
    @GetMapping("/api/service-offers/matching")
    public Flux<ServiceOffer> findMatching(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(defaultValue = "0") double weightKg,
            @RequestParam(defaultValue = "0") double distanceKm) {
        return findMatchingUseCase.findMatchingOffers(tenantId, weightKg, distanceKm);
    }

    @Operation(summary = "Compare multiple service offers side by side")
    @PostMapping("/api/service-offers/compare")
    public Mono<OfferComparison> compareOffers(@RequestBody CompareOffersRequest body) {
        return compareOffersUseCase.compareOffers(
                body.offerIds().stream().map(UUID::fromString).toList());
    }

    @Operation(summary = "Publish a service offer to the marketplace")
    @PostMapping("/api/service-offers/{offerId}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> publishOffer(@PathVariable UUID offerId) {
        return publishOfferUseCase.publishToMarket(offerId);
    }

    @Operation(summary = "Unpublish (withdraw) a service offer from the marketplace")
    @DeleteMapping("/api/service-offers/{offerId}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> unpublishOffer(@PathVariable UUID offerId) {
        return publishOfferUseCase.unpublishFromMarket(offerId);
    }

    // ── Request DTOs ─────────────────────────────────────────────────────────

    public record CreateOfferRequest(
            String providerId,
            String catalogProductId,
            String name,
            String description,
            String type,
            Double maxWeightKg,
            Double maxDistanceKm,
            Integer deliveryWindowHours,
            String coverageZoneId,
            String policyId
    ) {}

    public record CompareOffersRequest(List<String> offerIds) {}
}
