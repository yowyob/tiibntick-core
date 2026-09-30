package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryStatusUpdateDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryStatusApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.delivery.DeliveryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verifies ownership guards on {@link GofpDeliveryController}:
 * {@code updateStatus} and {@code cancelDelivery}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GofpDeliveryController — ownership guards (updateStatus, cancel)")
class GofpDeliveryControllerOwnershipTest {

    static final UUID DELIVERY_ID      = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    static final UUID ASSIGNED_ACTOR   = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    static final UUID UNASSIGNED_ACTOR = UUID.fromString("cccccccc-0000-0000-0000-000000000001");

    @Mock DeliveryUseCase deliveryUseCase;
    @Mock DeliveryStatusApplicationService deliveryStatusApplicationService;

    private WebTestClient clientFor(UUID actorId) {
        TntSecurityContext ctx = TntSecurityContext.builder().userId(actorId).build();

        GofpDeliveryController controller =
                new GofpDeliveryController(deliveryUseCase, deliveryStatusApplicationService);
        return WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new FixedContextResolver(ctx)))
                .build();
    }

    @Test
    @DisplayName("assigned actor with valid OTP → ownership check passes → 200")
    void assignedActorGets200() {
        Delivery saved = new Delivery();
        saved.setId(DELIVERY_ID);
        saved.setStatus(DeliveryStatus.DELIVERED);

        when(deliveryStatusApplicationService.assertCallerIsAssignedDeliveryPerson(
                eq(DELIVERY_ID), eq(ASSIGNED_ACTOR)))
                .thenReturn(Mono.empty());
        when(deliveryStatusApplicationService.updateStatus(eq(DELIVERY_ID), any()))
                .thenReturn(Mono.just(saved));

        clientFor(ASSIGNED_ACTOR)
                .patch().uri("/api/v1/deliveries/{id}/status", DELIVERY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"DELIVERED\",\"confirmationCode\":\"123456\"}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("unassigned actor with valid OTP → ownership check fails → 403")
    void unassignedActorGets403() {
        when(deliveryStatusApplicationService.assertCallerIsAssignedDeliveryPerson(
                eq(DELIVERY_ID), eq(UNASSIGNED_ACTOR)))
                .thenReturn(Mono.error(new AccessDeniedException(
                        "Caller " + UNASSIGNED_ACTOR + " is not the assigned delivery person")));

        clientFor(UNASSIGNED_ACTOR)
                .patch().uri("/api/v1/deliveries/{id}/status", DELIVERY_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"DELIVERED\",\"confirmationCode\":\"123456\"}")
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── cancel: livreur assigné OR expéditeur ────────────────────────────────

    @Test
    @DisplayName("cancelDelivery — authorised actor (livreur or expéditeur) → 200")
    void cancel_authorisedActorGets200() {
        DeliveryResponseDTO dto = new DeliveryResponseDTO();
        when(deliveryStatusApplicationService.assertCallerCanCancelDelivery(
                eq(DELIVERY_ID), eq(ASSIGNED_ACTOR)))
                .thenReturn(Mono.empty());
        when(deliveryUseCase.cancelDelivery(DELIVERY_ID))
                .thenReturn(Mono.just(dto));

        clientFor(ASSIGNED_ACTOR)
                .patch().uri("/api/v1/deliveries/{id}/cancel", DELIVERY_ID)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("cancelDelivery — third party → 403")
    void cancel_thirdPartyGets403() {
        when(deliveryStatusApplicationService.assertCallerCanCancelDelivery(
                eq(DELIVERY_ID), eq(UNASSIGNED_ACTOR)))
                .thenReturn(Mono.error(new AccessDeniedException(
                        "neither delivery person nor sender")));

        clientFor(UNASSIGNED_ACTOR)
                .patch().uri("/api/v1/deliveries/{id}/cancel", DELIVERY_ID)
                .exchange()
                .expectStatus().isForbidden();
    }

    /**
     * When the delivery has no announcementId (created from a DeliveryNeed direct flow),
     * assertCallerCanCancelDelivery restricts cancellation to the assigned delivery person only
     * (isSender branch unconditionally returns false). A non-owner calling cancel gets 403.
     *
     * <p>This test covers the contract at the controller level. The service-level behaviour
     * (isSender=Mono.just(false) when annId==null) is the authoritative decision point.</p>
     */
    @Test
    @DisplayName("cancelDelivery — null announcementId, non-owner → 403 (livreur-only restriction)")
    void cancel_nullAnnouncementId_nonOwnerGets403() {
        when(deliveryStatusApplicationService.assertCallerCanCancelDelivery(
                eq(DELIVERY_ID), eq(UNASSIGNED_ACTOR)))
                .thenReturn(Mono.error(new AccessDeniedException(
                        "Caller is neither the assigned delivery person"
                        + " nor the sender of delivery " + DELIVERY_ID)));

        clientFor(UNASSIGNED_ACTOR)
                .patch().uri("/api/v1/deliveries/{id}/cancel", DELIVERY_ID)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── Minimal @CurrentUser resolver for WebTestClient without full Spring Security ─────────
    static final class FixedContextResolver implements HandlerMethodArgumentResolver {
        private final TntSecurityContext ctx;
        FixedContextResolver(TntSecurityContext ctx) { this.ctx = ctx; }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(TntSecurityContext.class)
                    && parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Mono<Object> resolveArgument(MethodParameter parameter,
                                            BindingContext bindingContext,
                                            ServerWebExchange exchange) {
            return Mono.justOrEmpty(ctx);
        }
    }
}
