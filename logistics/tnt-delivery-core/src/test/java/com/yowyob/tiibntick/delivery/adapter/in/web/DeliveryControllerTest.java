package com.yowyob.tiibntick.delivery.adapter.in.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.delivery.adapter.in.web.DeliveryController;
import com.yowyob.tiibntick.core.delivery.adapter.in.web.DeliveryExceptionHandler;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryLifecycleUseCase;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * WebFlux tests for {@code DeliveryController} — Mockito pur, sans Spring Boot Test.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class DeliveryControllerTest {

    @Mock DeliveryLifecycleUseCase lifecycleUseCase;
    @Mock DeliveryQueryUseCase queryUseCase;

    @InjectMocks DeliveryController controller;

    WebTestClient webTestClient;

    private static final UUID TENANT_ID   = UUID.randomUUID();
    private static final UUID DELIVERY_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient
            .bindToController(controller)
            .controllerAdvice(new DeliveryExceptionHandler())
            .argumentResolvers(resolvers -> 
                resolvers.addCustomResolver(new CurrentUserArgumentResolver()))
            .build();
    }

    static class CurrentUserArgumentResolver implements org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver {
        
        @Override
        public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                return parameter.hasParameterAnnotation(
                com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser.class);
        }
        
        @Override
        public Mono<Object> resolveArgument(
            org.springframework.core.MethodParameter parameter,
            org.springframework.web.reactive.BindingContext bindingContext,
            org.springframework.web.server.ServerWebExchange exchange) {
                
            return Mono.just(new TntUserIdentity(
                UUID.randomUUID(),   // userId
                UUID.randomUUID(),   // tenantId
                UUID.randomUUID(),   // actorId
                null,                // organizationId
                null,                // agencyId
                Set.of("delivery:read", "delivery:write"),  // permissions
                false                // freelancer ← IMPORTANT
            ));
        }
    }



    @Test
    @DisplayName("GET /deliveries/{id} should return 200 when delivery exists")
    void shouldReturnDeliveryById() {
        Delivery delivery = buildTestDelivery();
        when(queryUseCase.findDeliveryById(TENANT_ID, DELIVERY_ID)).thenReturn(Mono.just(delivery));

        webTestClient.get()
                .uri("/api/v1/tenants/{t}/deliveries/{d}", TENANT_ID, DELIVERY_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("CREATED");
    }

    @Test
    @DisplayName("GET /deliveries/{id} should return 404 when delivery not found")
    void shouldReturn404WhenNotFound() {
        when(queryUseCase.findDeliveryById(TENANT_ID, DELIVERY_ID))
                .thenReturn(Mono.error(new DeliveryNotFoundException(DELIVERY_ID)));

        webTestClient.get()
                .uri("/api/v1/tenants/{t}/deliveries/{d}", TENANT_ID, DELIVERY_ID)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("POST /deliveries/{id}/pickup should return updated delivery")
    void shouldConfirmPickup() {
        Delivery updated = buildTestDelivery();
        updated.confirmPickup();
        when(lifecycleUseCase.confirmPickup(any())).thenReturn(Mono.just(updated));

        webTestClient.post()
                .uri("/api/v1/tenants/{t}/deliveries/{d}/pickup", TENANT_ID, DELIVERY_ID)
                .header("X-Delivery-Person-Id", UUID.randomUUID().toString())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PICKED_UP");
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Delivery buildTestDelivery() {
        Parcel parcel = Parcel.create(new PackageSpecification(
                1.5, 20, 15, 10, false, false, "Test"));
        return Delivery.create(
                TENANT_ID, null, UUID.randomUUID(), parcel,
                new DeliveryAddress(null, "Mokolo", "Mokolo", "Yaoundé", "CM",
                        new GeoCoordinates(3.87, 11.52)),
                new DeliveryAddress(null, "Bastos", "Bastos", "Yaoundé", "CM",
                        new GeoCoordinates(3.88, 11.53)),
                new RecipientInfo("Test", "+237690000001", null),
                DeliveryUrgency.STANDARD, null, null);
    }
}
