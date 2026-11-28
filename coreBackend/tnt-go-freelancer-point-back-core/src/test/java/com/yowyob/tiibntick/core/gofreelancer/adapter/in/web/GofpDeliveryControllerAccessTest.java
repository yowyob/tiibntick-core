package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryStatusApplicationService;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot C-45 — {@link GofpDeliveryController} asks the access rules before every read by id,
 * announcement or delivery need, before live tracking by announcement, and before an update;
 * the list by status is staff-only. The rules themselves are covered by DeliveryAccessRulesTest.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GofpDeliveryController — reads and price limited to the parties of the delivery")
class GofpDeliveryControllerAccessTest {

    static final UUID DELIVERY = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000451");
    static final UUID ANNOUNCEMENT = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000451");
    static final UUID NEED = UUID.fromString("cccccccc-0000-0000-0000-000000000451");
    static final UUID STRANGER = UUID.fromString("33333333-0000-0000-0000-000000000451");

    @Mock DeliveryUseCase deliveryUseCase;
    @Mock DeliveryStatusApplicationService rules;

    @BeforeEach
    void stranger() {
        AccessDeniedException no = new AccessDeniedException("not a party of this delivery");
        when(rules.assertCallerCanReadDelivery(eq(DELIVERY), any())).thenReturn(Mono.error(no));
        when(rules.assertCallerCanReadDeliveryOfAnnouncement(eq(ANNOUNCEMENT), any())).thenReturn(Mono.error(no));
        when(rules.assertCallerCanReadDeliveryOfNeed(eq(NEED), any())).thenReturn(Mono.error(no));
        when(rules.assertCallerCanUpdateDelivery(eq(DELIVERY), anyBoolean(), any())).thenReturn(Mono.error(no));
        when(deliveryUseCase.getDeliveryById(any())).thenReturn(Mono.just(new DeliveryResponseDTO()));
        when(deliveryUseCase.getDeliveryByAnnouncementId(any())).thenReturn(Mono.just(new DeliveryResponseDTO()));
        when(deliveryUseCase.getDeliveryByDeliveryNeedId(any())).thenReturn(Mono.just(new DeliveryResponseDTO()));
        when(deliveryUseCase.getDeliveriesByStatus(any())).thenReturn(Flux.empty());
        when(deliveryUseCase.updateDelivery(any(), any())).thenReturn(Mono.just(new DeliveryResponseDTO()));
        when(deliveryUseCase.trackDelivery(any())).thenReturn(Mono.empty());
        when(deliveryUseCase.getDeliveryAssistance(any())).thenReturn(Mono.empty());
    }

    private WebTestClient as(String... roles) {
        TntSecurityContext ctx = TntSecurityContext.builder().userId(STRANGER).authenticated(true)
                .roles(Set.of(roles)).build();
        return WebTestClient.bindToController(new GofpDeliveryController(deliveryUseCase, rules))
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new FixedContextResolver(ctx)))
                .build();
    }

    @Test
    @DisplayName("an ordinary user changing a delivery's price is refused and nothing is saved")
    void priceChangeRefused() {
        as("ROLE_CLIENT").put().uri("/api/v1/deliveries/{id}", DELIVERY)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"tarif\":1}")
                .exchange().expectStatus().isForbidden();
        verify(deliveryUseCase, never()).updateDelivery(any(), any());
        verify(rules).assertCallerCanUpdateDelivery(eq(DELIVERY), eq(true), any());
    }

    @Test
    @DisplayName("a third party reading a delivery by id, announcement or delivery need gets 403")
    void thirdPartyReadsRefused() {
        WebTestClient c = as("ROLE_FREELANCER");
        c.get().uri("/api/v1/deliveries/{id}", DELIVERY).exchange().expectStatus().isForbidden();
        c.get().uri("/api/v1/deliveries/announcement/{id}", ANNOUNCEMENT).exchange().expectStatus().isForbidden();
        c.get().uri("/api/v1/deliveries/delivery-need/{id}", NEED).exchange().expectStatus().isForbidden();
        c.get().uri("/api/v1/deliveries/{id}/assistance", DELIVERY).exchange().expectStatus().isForbidden();
        verify(deliveryUseCase, never()).getDeliveryById(any());
        verify(deliveryUseCase, never()).getDeliveryByAnnouncementId(any());
        verify(deliveryUseCase, never()).getDeliveryByDeliveryNeedId(any());
    }

    @Test
    @DisplayName("a third party cannot follow a delivery live by its announcement")
    void thirdPartyTrackingRefused() {
        as("ROLE_CLIENT").get().uri("/api/v1/deliveries/tracking/announcement/{id}", ANNOUNCEMENT)
                .exchange().expectStatus().isForbidden();
        as("ROLE_CLIENT").get().uri("/api/v1/deliveries/tracking/stream/announcement/{id}", ANNOUNCEMENT)
                .exchange().expectStatus().isForbidden();
        verify(deliveryUseCase, never()).trackDelivery(any());
        verify(deliveryUseCase, never()).trackDeliveryStream(any());
    }

    @Test
    @DisplayName("listing deliveries by status is for staff only")
    void listByStatusStaffOnly() {
        as("ROLE_CLIENT").get().uri("/api/v1/deliveries/status/{s}", DeliveryStatus.IN_TRANSIT)
                .exchange().expectStatus().isForbidden();
        as("ROLE_SUPPORT_AGENT").get().uri("/api/v1/deliveries/status/{s}", DeliveryStatus.IN_TRANSIT)
                .exchange().expectStatus().isOk();
        verify(deliveryUseCase).getDeliveriesByStatus(DeliveryStatus.IN_TRANSIT);
    }

    @Test
    @DisplayName("when the rules allow it, the read goes through")
    void partyReads() {
        when(rules.assertCallerCanReadDelivery(eq(DELIVERY), any())).thenReturn(Mono.empty());
        as("ROLE_CLIENT").get().uri("/api/v1/deliveries/{id}", DELIVERY).exchange().expectStatus().isOk();
        verify(deliveryUseCase).getDeliveryById(DELIVERY);
    }

    static class FixedContextResolver implements HandlerMethodArgumentResolver {
        private final TntSecurityContext ctx;

        FixedContextResolver(TntSecurityContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class)
                    && TntSecurityContext.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Mono<Object> resolveArgument(MethodParameter p, BindingContext c, ServerWebExchange e) {
            return Mono.just(ctx);
        }
    }
}
