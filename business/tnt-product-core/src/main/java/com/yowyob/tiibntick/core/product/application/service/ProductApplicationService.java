package com.yowyob.tiibntick.core.product.application.service;

import com.yowyob.tiibntick.core.product.application.port.in.ActivateProductUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.ArchiveProductUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.CreateProductCommand;
import com.yowyob.tiibntick.core.product.application.port.in.CreateProductUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.GetProductUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.ListProductsByTenantUseCase;
import com.yowyob.tiibntick.core.product.application.port.out.KernelProductPort;
import com.yowyob.tiibntick.core.product.application.port.out.ProductEventPublisherPort;
import com.yowyob.tiibntick.core.product.application.port.out.ProductRepository;
import com.yowyob.tiibntick.core.product.domain.event.ProductCreatedEvent;
import com.yowyob.tiibntick.core.product.domain.exception.ProductNotFoundException;
import com.yowyob.tiibntick.core.product.domain.model.Product;
import com.yowyob.tiibntick.common.exception.TntConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service for Product management in TiiBnTick.
 *
 * <p>Implements the full product lifecycle: DRAFT → ACTIVE → ARCHIVED (and OUT_OF_STOCK).
 * All state transitions are delegated to the {@link Product} aggregate.
 *
 * <p><strong>Kernel integration:</strong> If {@code catalogProductId} is provided in
 * {@link CreateProductCommand}, the Kernel product catalog is queried via {@link KernelProductPort}
 * to validate existence before saving. TNT product creation succeeds even if the Kernel is
 * temporarily unavailable (fail-open strategy — catalogProductId remains set but unverified).
 *
 * @author MANFOUO Braun
 */
@Service
public class ProductApplicationService implements
        CreateProductUseCase,
        GetProductUseCase,
        ListProductsByTenantUseCase,
        ActivateProductUseCase,
        ArchiveProductUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProductApplicationService.class);

    private final ProductRepository productRepository;
    private final ProductEventPublisherPort eventPublisher;
    private final KernelProductPort kernelProductPort;

    public ProductApplicationService(ProductRepository productRepository,
                                     ProductEventPublisherPort eventPublisher,
                                     KernelProductPort kernelProductPort) {
        this.productRepository  = productRepository;
        this.eventPublisher     = eventPublisher;
        this.kernelProductPort  = kernelProductPort;
    }

    @Transactional
    @Override
    public Mono<Product> createProduct(CreateProductCommand cmd) {
        return productRepository.existsBySku(cmd.tenantId(), cmd.sku())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new TntConflictException("PRODUCT_SKU_CONFLICT",
                                "A product with SKU '" + cmd.sku() + "' already exists for this tenant"));
                    }

                    // If catalogProductId is provided, validate existence in Kernel (fail-open)
                    Mono<Void> kernelValidation = (cmd.catalogProductId() != null)
                            ? kernelProductPort.existsAndActive(cmd.catalogProductId())
                                    .flatMap(active -> {
                                        if (!active) {
                                            log.warn("Kernel product {} not found or inactive — " +
                                                    "creating TNT product without Kernel validation",
                                                    cmd.catalogProductId());
                                        }
                                        return Mono.empty();
                                    })
                            : Mono.empty();

                    return kernelValidation.then(Mono.defer(() -> {
                        Product product = Product.create(
                                cmd.tenantId(), cmd.catalogProductId(),
                                cmd.sku(), cmd.name(), cmd.description(),
                                cmd.categoryId(), cmd.type(), cmd.basePrice(), cmd.unit(),
                                cmd.weightKg(), cmd.dimensions(), cmd.logisticsProfile(),
                                cmd.tags(), cmd.attributes());
                        return productRepository.save(product)
                                .flatMap(saved -> eventPublisher
                                        .publishProductCreated(ProductCreatedEvent.of(
                                                saved.id(), saved.tenantId(), saved.sku(), saved.name()))
                                        .thenReturn(saved));
                    }));
                });
    }

    @Override
    public Mono<Product> getProduct(UUID productId) {
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(productId)));
    }

    @Override
    public Flux<Product> listProductsByTenant(UUID tenantId) {
        return productRepository.findByTenantId(tenantId);
    }

    @Override
    public Mono<Void> activateProduct(UUID productId) {
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(productId)))
                .map(Product::activate)
                .flatMap(productRepository::save)
                .then();
    }

    @Override
    public Mono<Void> archiveProduct(UUID productId) {
        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(productId)))
                .map(Product::archive)
                .flatMap(productRepository::save)
                .then();
    }
}
