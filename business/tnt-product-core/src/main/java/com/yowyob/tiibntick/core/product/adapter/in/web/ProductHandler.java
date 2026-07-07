package com.yowyob.tiibntick.core.product.adapter.in.web;

import com.yowyob.tiibntick.common.vo.Money;
import com.yowyob.tiibntick.core.product.application.port.in.*;
import com.yowyob.tiibntick.core.product.domain.model.LogisticsProfile;
import com.yowyob.tiibntick.core.product.domain.model.ProductType;
import com.yowyob.tiibntick.core.product.domain.model.ServiceType;
import com.yowyob.tiibntick.core.product.domain.model.UnitOfMeasure;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WebFlux functional handler for TiiBnTick product and service offer endpoints.
 *
 * <p>Uses {@link Money} from {@code tnt-common-core} for all monetary parsing.
 * HTTP request bodies carry price as a {@code double} which is converted to
 * {@code BigDecimal} before constructing the {@code Money} value object.
 *
 * @author MANFOUO Braun
 */
@Component
public class ProductHandler {

    private final CreateProductUseCase createProductUseCase;
    private final GetProductUseCase getProductUseCase;
    private final ListProductsByTenantUseCase listProductsUseCase;
    private final ActivateProductUseCase activateProductUseCase;
    private final ArchiveProductUseCase archiveProductUseCase;
    private final CreateServiceOfferUseCase createOfferUseCase;
    private final GetServiceOfferUseCase getOfferUseCase;
    private final ListServiceOffersByProviderUseCase listOffersUseCase;
    private final PublishServiceOfferUseCase publishOfferUseCase;
    private final FindMatchingOffersUseCase findMatchingUseCase;
    private final CompareOffersUseCase compareOffersUseCase;

    public ProductHandler(CreateProductUseCase createProductUseCase,
                          GetProductUseCase getProductUseCase,
                          ListProductsByTenantUseCase listProductsUseCase,
                          ActivateProductUseCase activateProductUseCase,
                          ArchiveProductUseCase archiveProductUseCase,
                          CreateServiceOfferUseCase createOfferUseCase,
                          GetServiceOfferUseCase getOfferUseCase,
                          ListServiceOffersByProviderUseCase listOffersUseCase,
                          PublishServiceOfferUseCase publishOfferUseCase,
                          FindMatchingOffersUseCase findMatchingUseCase,
                          CompareOffersUseCase compareOffersUseCase) {
        this.createProductUseCase = createProductUseCase;
        this.getProductUseCase = getProductUseCase;
        this.listProductsUseCase = listProductsUseCase;
        this.activateProductUseCase = activateProductUseCase;
        this.archiveProductUseCase = archiveProductUseCase;
        this.createOfferUseCase = createOfferUseCase;
        this.getOfferUseCase = getOfferUseCase;
        this.listOffersUseCase = listOffersUseCase;
        this.publishOfferUseCase = publishOfferUseCase;
        this.findMatchingUseCase = findMatchingUseCase;
        this.compareOffersUseCase = compareOffersUseCase;
    }

    public Mono<ServerResponse> createProduct(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
        return request.bodyToMono(CreateProductRequest.class)
                .flatMap(body -> {
                    // Parse catalogProductId if provided — links product to Kernel catalog
                    UUID catalogProductId = (body.catalogProductId() != null)
                            ? UUID.fromString(body.catalogProductId()) : null;

                    // Convert double to BigDecimal for tnt-common-core Money (no of(double,String))
                    Money basePrice = Money.of(BigDecimal.valueOf(body.basePriceAmount()),
                            body.basePriceCurrency());

                    CreateProductCommand cmd = new CreateProductCommand(
                            tenantId, catalogProductId,
                            body.sku(), body.name(), body.description(),
                            body.categoryId() != null ? UUID.fromString(body.categoryId()) : null,
                            ProductType.valueOf(body.type()),
                            basePrice,
                            UnitOfMeasure.valueOf(body.unit()),
                            body.weightKg(), null, LogisticsProfile.standard(),
                            body.tags() != null ? body.tags() : List.of(),
                            body.attributes() != null ? body.attributes() : Map.of());
                    return createProductUseCase.createProduct(cmd);
                })
                .flatMap(p -> ServerResponse.status(HttpStatus.CREATED).bodyValue(p))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage())));
    }

    public Mono<ServerResponse> getProduct(ServerRequest request) {
        UUID productId = UUID.fromString(request.pathVariable("id"));
        return getProductUseCase.getProduct(productId)
                .flatMap(p -> ServerResponse.ok().bodyValue(p))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> listProducts(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
        return listProductsUseCase.listProductsByTenant(tenantId)
                .collectList()
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> activateProduct(ServerRequest request) {
        UUID productId = UUID.fromString(request.pathVariable("id"));
        return activateProductUseCase.activateProduct(productId)
                .then(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> archiveProduct(ServerRequest request) {
        UUID productId = UUID.fromString(request.pathVariable("id"));
        return archiveProductUseCase.archiveProduct(productId)
                .then(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> createServiceOffer(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
        return request.bodyToMono(CreateOfferRequest.class)
                .flatMap(body -> {
                    UUID catalogProductId = (body.catalogProductId() != null)
                            ? UUID.fromString(body.catalogProductId()) : null;

                    CreateServiceOfferCommand cmd = new CreateServiceOfferCommand(
                            tenantId, UUID.fromString(body.providerId()), catalogProductId,
                            body.name(), body.description(), ServiceType.valueOf(body.type()),
                            body.maxWeightKg(), body.maxDistanceKm(), body.deliveryWindowHours(),
                            body.coverageZoneId() != null ? UUID.fromString(body.coverageZoneId()) : null,
                            body.policyId());
                    return createOfferUseCase.createServiceOffer(cmd);
                })
                .flatMap(o -> ServerResponse.status(HttpStatus.CREATED).bodyValue(o));
    }

    public Mono<ServerResponse> getServiceOffer(ServerRequest request) {
        UUID offerId = UUID.fromString(request.pathVariable("id"));
        return getOfferUseCase.getServiceOffer(offerId)
                .flatMap(o -> ServerResponse.ok().bodyValue(o))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> listOffersByProvider(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
        UUID providerId = UUID.fromString(request.pathVariable("providerId"));
        return listOffersUseCase.listByProvider(tenantId, providerId)
                .collectList()
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> publishOffer(ServerRequest request) {
        UUID offerId = UUID.fromString(request.pathVariable("id"));
        return publishOfferUseCase.publishToMarket(offerId)
                .then(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> unpublishOffer(ServerRequest request) {
        UUID offerId = UUID.fromString(request.pathVariable("id"));
        return publishOfferUseCase.unpublishFromMarket(offerId)
                .then(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> findMatchingOffers(ServerRequest request) {
        UUID tenantId = UUID.fromString(request.headers().firstHeader("X-Tenant-Id"));
        double weightKg = Double.parseDouble(request.queryParam("weightKg").orElse("0"));
        double distanceKm = Double.parseDouble(request.queryParam("distanceKm").orElse("0"));
        return findMatchingUseCase.findMatchingOffers(tenantId, weightKg, distanceKm)
                .collectList()
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> compareOffers(ServerRequest request) {
        return request.bodyToMono(CompareOffersRequest.class)
                .flatMap(body -> compareOffersUseCase.compareOffers(
                        body.offerIds().stream().map(UUID::fromString).toList()))
                .flatMap(comparison -> ServerResponse.ok().bodyValue(comparison));
    }

    // ── Inner request records ────────────────────────────────────────────────────

    /**
     * HTTP request body for product creation.
     * {@code catalogProductId}: optional UUID string — links to Kernel product catalog.
     */
    record CreateProductRequest(
            String sku, String name, String description,
            String categoryId,
            /** Optional UUID — links to Kernel catalog product (RT-comops-product-core). */
            String catalogProductId,
            String type, double basePriceAmount, String basePriceCurrency,
            String unit, Double weightKg, List<String> tags, Map<String, String> attributes
    ) {}

    /**
     * HTTP request body for service offer creation.
     * {@code catalogProductId}: optional UUID string — links to Kernel product this offer handles.
     */
    record CreateOfferRequest(
            String providerId,
            /** Optional UUID — links to Kernel catalog product this offer is designed to transport. */
            String catalogProductId,
            String name, String description, String type,
            double maxWeightKg, Double maxDistanceKm, int deliveryWindowHours,
            String coverageZoneId, String policyId
    ) {}

    record CompareOffersRequest(List<String> offerIds) {}
}
