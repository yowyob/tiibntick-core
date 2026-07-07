package com.yowyob.tiibntick.core.product.application;

import com.yowyob.tiibntick.core.product.application.port.in.CreateServiceOfferCommand;
import com.yowyob.tiibntick.core.product.application.port.out.KernelProductPort;
import com.yowyob.tiibntick.core.product.application.port.out.ProductEventPublisherPort;
import com.yowyob.tiibntick.core.product.application.port.out.ServiceOfferRepository;
import com.yowyob.tiibntick.core.product.application.service.ServiceOfferApplicationService;
import com.yowyob.tiibntick.core.product.domain.model.ProductStatus;
import com.yowyob.tiibntick.core.product.domain.model.ServiceOffer;
import com.yowyob.tiibntick.core.product.domain.model.ServiceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ServiceOfferApplicationService.
 * Verifies Kernel integration, catalogProductId handling, and offer lifecycle.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class ServiceOfferApplicationServiceTest {

    @Mock
    private ServiceOfferRepository offerRepository;
    @Mock
    private ProductEventPublisherPort eventPublisher;
    @Mock
    private KernelProductPort kernelProductPort;

    @InjectMocks
    private ServiceOfferApplicationService service;

    @Test
    void should_create_service_offer_without_catalog_product_id() {
        UUID tenantId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        when(offerRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        CreateServiceOfferCommand cmd = new CreateServiceOfferCommand(
                tenantId, providerId, "Express Yaoundé", "Fast express within Yaoundé",
                ServiceType.EXPRESS_DELIVERY, 50.0, 30.0, 4, null, null);

        StepVerifier.create(service.createServiceOffer(cmd))
                .assertNext(offer -> {
                    assertThat(offer.name()).isEqualTo("Express Yaoundé");
                    assertThat(offer.status()).isEqualTo(ProductStatus.DRAFT);
                    assertThat(offer.maxWeightKg()).isEqualTo(50.0);
                    assertThat(offer.isPublishedOnMarket()).isFalse();
                    assertThat(offer.catalogProductId()).isNull(); // no Kernel link
                })
                .verifyComplete();
        verifyNoInteractions(kernelProductPort);
    }

    @Test
    void should_create_service_offer_with_catalog_product_id() {
        UUID tenantId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID catalogProductId = UUID.randomUUID();

        when(offerRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        CreateServiceOfferCommand cmd = new CreateServiceOfferCommand(
                tenantId, providerId, catalogProductId,
                "Fragile Specialist", "For fragile items only",
                ServiceType.FRAGILE_SPECIALIST, 20.0, 15.0, 6, null, null);

        StepVerifier.create(service.createServiceOffer(cmd))
                .assertNext(offer -> {
                    assertThat(offer.catalogProductId()).isEqualTo(catalogProductId);
                    assertThat(offer.type()).isEqualTo(ServiceType.FRAGILE_SPECIALIST);
                })
                .verifyComplete();
    }

    @Test
    void should_validate_kernel_product_on_publish_when_catalog_id_set() {
        UUID tenantId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID catalogProductId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();

        ServiceOffer offer = ServiceOffer.rehydrate(offerId, tenantId, providerId, catalogProductId,
                "Express", null, ServiceType.EXPRESS_DELIVERY, 50.0, null, 4,
                null, null, false, ProductStatus.DRAFT,
                java.time.Instant.now(), java.time.Instant.now());

        when(offerRepository.findById(offerId)).thenReturn(Mono.just(offer));
        when(kernelProductPort.existsAndActive(catalogProductId)).thenReturn(Mono.just(true));
        when(offerRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishServiceOfferPublished(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.publishToMarket(offerId))
                .verifyComplete();

        verify(kernelProductPort, times(1)).existsAndActive(catalogProductId);
    }

    @Test
    void should_not_call_kernel_on_publish_when_no_catalog_id() {
        UUID tenantId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();

        // No catalogProductId
        ServiceOffer offer = ServiceOffer.rehydrate(offerId, tenantId, providerId, null,
                "Standard", null, ServiceType.STANDARD_DELIVERY, 100.0, null, 24,
                null, null, false, ProductStatus.DRAFT,
                java.time.Instant.now(), java.time.Instant.now());

        when(offerRepository.findById(offerId)).thenReturn(Mono.just(offer));
        when(offerRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventPublisher.publishServiceOfferPublished(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.publishToMarket(offerId))
                .verifyComplete();

        verifyNoInteractions(kernelProductPort);
    }

    @Test
    void should_compare_offers() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        ServiceOffer offer1 = ServiceOffer.create(tenantId, providerId, "Standard", null,
                ServiceType.STANDARD_DELIVERY, 100.0, null, 24, null, null).activate();
        ServiceOffer offer2 = ServiceOffer.create(tenantId, providerId, "Express", null,
                ServiceType.EXPRESS_DELIVERY, 30.0, 50.0, 4, null, null).activate();

        when(offerRepository.findAllById(List.of(id1, id2)))
                .thenReturn(Flux.fromIterable(List.of(offer1, offer2)));

        StepVerifier.create(service.compareOffers(List.of(id1, id2)))
                .assertNext(comparison -> {
                    assertThat(comparison.size()).isEqualTo(2);
                    assertThat(comparison.compareByDeliverySpeed().getFirst().deliveryWindowHours())
                            .isEqualTo(4);
                })
                .verifyComplete();
    }

    @Test
    void should_find_matching_offers_for_mission() {
        UUID tenantId = UUID.randomUUID();
        UUID providerId = UUID.randomUUID();

        ServiceOffer matching = ServiceOffer.create(tenantId, providerId, "Heavy Freight", null,
                ServiceType.FREIGHT_HEAVY, 500.0, 200.0, 48, null, null).activate();

        when(offerRepository.findMatchingForMission(tenantId, 100.0, 50.0))
                .thenReturn(Flux.just(matching));

        StepVerifier.create(service.findMatchingOffers(tenantId, 100.0, 50.0))
                .assertNext(offer -> assertThat(offer.type()).isEqualTo(ServiceType.FREIGHT_HEAVY))
                .verifyComplete();
    }
}
