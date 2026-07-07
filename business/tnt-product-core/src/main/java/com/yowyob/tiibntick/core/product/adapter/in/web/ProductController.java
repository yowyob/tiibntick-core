package com.yowyob.tiibntick.core.product.adapter.in.web;

import com.yowyob.tiibntick.common.vo.Money;
import com.yowyob.tiibntick.core.product.application.port.in.*;
import com.yowyob.tiibntick.core.product.domain.model.LogisticsProfile;
import com.yowyob.tiibntick.core.product.domain.model.Product;
import com.yowyob.tiibntick.core.product.domain.model.ProductType;
import com.yowyob.tiibntick.core.product.domain.model.UnitOfMeasure;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for TiiBnTick logistics product catalog management.
 * Path aligned with Kernel Core's {@code /api/products} convention.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Products", description = "TiiBnTick logistics product catalog management")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

    private final CreateProductUseCase createProductUseCase;
    private final GetProductUseCase getProductUseCase;
    private final ListProductsByTenantUseCase listProductsUseCase;
    private final ActivateProductUseCase activateProductUseCase;
    private final ArchiveProductUseCase archiveProductUseCase;

    @Operation(summary = "List products for a tenant")
    @GetMapping
    public Flux<Product> listProducts(@RequestHeader("X-Tenant-Id") UUID tenantId) {
        return listProductsUseCase.listProductsByTenant(tenantId);
    }

    @Operation(summary = "Create a new logistics product")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Product> createProduct(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestBody CreateProductRequest body) {
        UUID catalogProductId = body.catalogProductId() != null
                ? UUID.fromString(body.catalogProductId()) : null;
        Money basePrice = Money.of(BigDecimal.valueOf(body.basePriceAmount()), body.basePriceCurrency());
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
    }

    @Operation(summary = "Search products by name or SKU prefix")
    @GetMapping("/search")
    public Flux<Product> search(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String type) {
        // Delegate to list + filter; a dedicated search use case can replace this later
        return listProductsUseCase.listProductsByTenant(tenantId)
                .filter(p -> name == null || p.name().toLowerCase().contains(name.toLowerCase()))
                .filter(p -> sku == null || p.sku().startsWith(sku.toUpperCase()))
                .filter(p -> type == null || p.type().name().equalsIgnoreCase(type));
    }

    @Operation(summary = "Get product by ID")
    @GetMapping("/{productId}")
    public Mono<Product> getProduct(@PathVariable UUID productId) {
        return getProductUseCase.getProduct(productId);
    }

    @Operation(summary = "Activate a product (DRAFT → ACTIVE)")
    @PostMapping("/{productId}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> activateProduct(@PathVariable UUID productId) {
        return activateProductUseCase.activateProduct(productId);
    }

    @Operation(summary = "Archive a product (→ ARCHIVED)")
    @PostMapping("/{productId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> archiveProduct(@PathVariable UUID productId) {
        return archiveProductUseCase.archiveProduct(productId);
    }

    @Operation(summary = "Delete (archive) a product")
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteProduct(@PathVariable UUID productId) {
        return archiveProductUseCase.archiveProduct(productId);
    }

    // ── Request DTOs ─────────────────────────────────────────────────────────

    public record CreateProductRequest(
            String sku,
            String name,
            String description,
            String categoryId,
            String catalogProductId,
            String type,
            Double basePriceAmount,
            String basePriceCurrency,
            String unit,
            Double weightKg,
            List<String> tags,
            Map<String, String> attributes
    ) {}
}
