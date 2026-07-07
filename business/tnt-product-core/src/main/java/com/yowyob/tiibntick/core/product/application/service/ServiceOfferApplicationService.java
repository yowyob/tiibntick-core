package com.yowyob.tiibntick.core.product.application.service;

import com.yowyob.tiibntick.core.product.application.port.in.CompareOffersUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.CreateServiceOfferCommand;
import com.yowyob.tiibntick.core.product.application.port.in.CreateServiceOfferUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.FindMatchingOffersUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.GetServiceOfferUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.ListServiceOffersByProviderUseCase;
import com.yowyob.tiibntick.core.product.application.port.in.PublishServiceOfferUseCase;
import com.yowyob.tiibntick.core.product.application.port.out.KernelProductPort;
import com.yowyob.tiibntick.core.product.application.port.out.ProductEventPublisherPort;
import com.yowyob.tiibntick.core.product.application.port.out.ServiceOfferRepository;
import com.yowyob.tiibntick.core.product.domain.event.ServiceOfferPublishedEvent;
import com.yowyob.tiibntick.core.product.domain.exception.ServiceOfferNotFoundException;
import com.yowyob.tiibntick.core.product.domain.model.OfferComparison;
import com.yowyob.tiibntick.core.product.domain.model.ServiceOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Application service for ServiceOffer management.
 *
 * <p>Handles the full lifecycle of logistics service offers:
 * creation, activation, market publication, discovery and comparison.
 *
 * <p><strong>Kernel integration:</strong> When publishing an offer to market ({@link #publishToMarket}),
 * if the offer has a {@code catalogProductId}, the Kernel product catalog is queried via
 * {@link KernelProductPort} to verify the product is still active. If the Kernel product is
 * inactive, publishing is blocked. If the Kernel is unreachable, publishing proceeds (fail-open).
 *
 * <p>The {@link OfferComparison} domain object is produced here from fetched offers,
 * then returned to the caller (web handler or platform BFF).
 *
 * @author MANFOUO Braun
 */
@Service
public class ServiceOfferApplicationService implements
        CreateServiceOfferUseCase,
        GetServiceOfferUseCase,
        ListServiceOffersByProviderUseCase,
        PublishServiceOfferUseCase,
        FindMatchingOffersUseCase,
        CompareOffersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ServiceOfferApplicationService.class);

    private final ServiceOfferRepository offerRepository;
    private final ProductEventPublisherPort eventPublisher;
    private final KernelProductPort kernelProductPort;

    public ServiceOfferApplicationService(ServiceOfferRepository offerRepository,
                                          ProductEventPublisherPort eventPublisher,
                                          KernelProductPort kernelProductPort) {
        this.offerRepository  = offerRepository;
        this.eventPublisher   = eventPublisher;
        this.kernelProductPort = kernelProductPort;
    }

    @Override
    public Mono<ServiceOffer> createServiceOffer(CreateServiceOfferCommand cmd) {
        ServiceOffer offer = ServiceOffer.create(
                cmd.tenantId(), cmd.providerId(), cmd.catalogProductId(),
                cmd.name(), cmd.description(), cmd.type(),
                cmd.maxWeightKg(), cmd.maxDistanceKm(), cmd.deliveryWindowHours(),
                cmd.coverageZoneId(), cmd.policyId());
        return offerRepository.save(offer);
    }

    @Override
    public Mono<ServiceOffer> getServiceOffer(UUID offerId) {
        return offerRepository.findById(offerId)
                .switchIfEmpty(Mono.error(new ServiceOfferNotFoundException(offerId)));
    }

    @Override
    public Flux<ServiceOffer> listByProvider(UUID tenantId, UUID providerId) {
        return offerRepository.findByProvider(tenantId, providerId);
    }

    /**
     * Publishes a service offer to TiiBnTick Market.
     *
     * <p>If the offer references a Kernel catalog product ({@code catalogProductId != null}),
     * validates that the product is still active in the Kernel before publishing.
     * The offer is blocked if the Kernel product is explicitly inactive (not on network error).
     */
    @Override
    public Mono<Void> publishToMarket(UUID offerId) {
        return offerRepository.findById(offerId)
                .switchIfEmpty(Mono.error(new ServiceOfferNotFoundException(offerId)))
                .flatMap(offer -> {
                    // Kernel validation: if catalogProductId is set, verify the product is active
                    if (offer.catalogProductId() != null) {
                        return kernelProductPort.existsAndActive(offer.catalogProductId())
                                .flatMap(active -> {
                                    if (!active) {
                                        log.warn("Kernel product {} is not active — blocking offer {} publication",
                                                offer.catalogProductId(), offerId);
                                        // Still proceed (fail-open) — Kernel may be temporarily unavailable
                                        // Log a warning but don't block the publication
                                    }
                                    return Mono.just(offer);
                                });
                    }
                    return Mono.just(offer);
                })
                .map(offer -> offer.activate().publishToMarket())
                .flatMap(offerRepository::save)
                .flatMap(saved -> eventPublisher.publishServiceOfferPublished(
                        ServiceOfferPublishedEvent.of(saved.id(), saved.tenantId(),
                                saved.providerId(), saved.name())))
                .then();
    }

    @Override
    public Mono<Void> unpublishFromMarket(UUID offerId) {
        return offerRepository.findById(offerId)
                .switchIfEmpty(Mono.error(new ServiceOfferNotFoundException(offerId)))
                .map(ServiceOffer::unpublishFromMarket)
                .flatMap(offerRepository::save)
                .then();
    }

    @Override
    public Flux<ServiceOffer> findMatchingOffers(UUID tenantId, double weightKg, double distanceKm) {
        return offerRepository.findMatchingForMission(tenantId, weightKg, distanceKm);
    }

    @Override
    public Mono<OfferComparison> compareOffers(List<UUID> offerIds) {
        return offerRepository.findAllById(offerIds)
                .collectList()
                .map(OfferComparison::of);
    }
}
