package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryStatusApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Traversal test for the ownership guard on
 * {@link GofpDeliveryController#trackDeliveryByNeed} and
 * {@link GofpDeliveryController#trackDeliveryByNeedStream}.
 *
 * <h3>SSE refusal design (lot 25.4/25.5)</h3>
 * {@code trackDeliveryByNeedStream} calls
 * {@link DeliveryUseCase#checkTrackingOwnership} as an eager {@code Mono} before
 * building the {@code ResponseEntity}. If the check fails, the
 * {@code GlobalExceptionHandler} returns 403 or 404 before any HTTP headers are
 * committed — 200 is never sent. The old {@code Mono.just(ResponseEntity.ok().body(body))}
 * pattern committed the 200 before the Flux was subscribed; on a real socket the guard
 * arrived too late even though it appeared to work in {@code MockServerHttpResponse}.
 *
 * <h3>REST + SSE alignment (lot 25.5)</h3>
 * Both routes now return 404 for an unknown need:
 * <ul>
 *   <li>REST: {@code onErrorResume(IllegalArgumentException.class, ...)} was removed;
 *       {@code GlobalExceptionHandler.handleIllegalArgument} maps "not found" → 404.</li>
 *   <li>SSE: {@code checkTrackingOwnership} fails with {@code IllegalArgumentException}
 *       → same handler → 404.</li>
 * </ul>
 *
 * <h3>Three cases (REST + SSE)</h3>
 * <ol>
 *   <li>Owner, no tracking data yet → 404 (REST empty Mono → {@code defaultIfEmpty(notFound)};
 *       SSE 200 is only reached after ownership passes).</li>
 *   <li>Non-owner → 403 before any data (both REST and SSE).</li>
 *   <li>Unknown need → 404, never 403 — existence must not leak via error code.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class GofpDeliveryControllerTrackingGuardTest {

    static final UUID NEED_ID        = UUID.fromString("22222222-0000-0000-0000-000000000001");
    static final UUID UNKNOWN_ID     = UUID.fromString("00000000-0000-0000-0000-000000000000");
    static final UUID OWNER_UUID     = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    static final UUID NON_OWNER_UUID = UUID.fromString("dddddddd-0000-0000-0000-000000000001");

    @Mock DeliveryUseCase deliveryUseCase;
    @Mock DeliveryStatusApplicationService deliveryStatusApplicationService;

    private WebTestClient clientFor(UUID callerId) {
        TntSecurityContext ctx = callerId != null
                ? TntSecurityContext.builder().userId(callerId).build()
                : TntSecurityContext.anonymous();

        GofpDeliveryController controller =
                new GofpDeliveryController(deliveryUseCase, deliveryStatusApplicationService);
        return WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new FixedContextResolver(ctx)))
                .build();
    }

    // ── REST: GET /tracking/delivery-need/{id} ───────────────────────────────

    /**
     * Owner, but no delivery linked to the need yet → service returns empty → 404.
     * The name "200" in the old test was wrong: Mono.empty() + defaultIfEmpty(notFound) = 404.
     */
    @Test
    void trackByNeed_rest_owner_noTrackingData_returns404() {
        when(deliveryUseCase.trackDeliveryByNeed(eq(NEED_ID), eq(OWNER_UUID)))
                .thenReturn(Mono.empty());

        clientFor(OWNER_UUID)
                .get().uri("/api/v1/deliveries/tracking/delivery-need/{id}", NEED_ID)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void trackByNeed_rest_nonOwner_returns403() {
        when(deliveryUseCase.trackDeliveryByNeed(eq(NEED_ID), eq(NON_OWNER_UUID)))
                .thenReturn(Mono.error(new AccessDeniedException("This delivery need belongs to another user")));

        clientFor(NON_OWNER_UUID)
                .get().uri("/api/v1/deliveries/tracking/delivery-need/{id}", NEED_ID)
                .exchange()
                .expectStatus().isForbidden();
    }

    /**
     * Unknown need → 404 via GlobalExceptionHandler (message contains "not found").
     * The old onErrorResume(IllegalArgumentException.class, ...) was returning 400 — removed
     * in lot 25.5 so REST and SSE are aligned on 404 for missing resources.
     */
    @Test
    void trackByNeed_rest_unknownNeed_returns404() {
        when(deliveryUseCase.trackDeliveryByNeed(eq(UNKNOWN_ID), eq(OWNER_UUID)))
                .thenReturn(Mono.error(new IllegalArgumentException("DeliveryNeed not found: " + UNKNOWN_ID)));

        clientFor(OWNER_UUID)
                .get().uri("/api/v1/deliveries/tracking/delivery-need/{id}", UNKNOWN_ID)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── SSE: GET /tracking/stream/delivery-need/{id} ─────────────────────────

    /**
     * Owner → checkTrackingOwnership passes → stream opened → 200.
     * trackDeliveryByNeedStream is only called inside Mono.fromSupplier after the check passes.
     */
    @Test
    void trackByNeedStream_sse_owner_returns200() {
        when(deliveryUseCase.checkTrackingOwnership(eq(NEED_ID), eq(OWNER_UUID)))
                .thenReturn(Mono.empty());
        when(deliveryUseCase.trackDeliveryByNeedStream(eq(NEED_ID), eq(OWNER_UUID)))
                .thenReturn(Flux.empty());

        clientFor(OWNER_UUID)
                .get().uri("/api/v1/deliveries/tracking/stream/delivery-need/{id}", NEED_ID)
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * Non-owner → checkTrackingOwnership fails → 403 BEFORE the stream opens.
     * trackDeliveryByNeedStream is never called (inside Mono.fromSupplier, not reached).
     *
     * <p><strong>Regression invariant:</strong> this test MUST fail if the controller
     * reverts to {@code Mono.just(ResponseEntity.ok().body(body))} (old pattern). In that
     * case {@code checkTrackingOwnership} would still be called but the ResponseEntity.ok()
     * would be committed first; on MockServerHttpResponse the test might still pass, but
     * on a real socket the 200 would already be written. The {@code Mono.fromSupplier}
     * pattern guarantees the check resolves before the 200 is committed on any transport.
     */
    @Test
    void trackByNeedStream_sse_nonOwner_returns403_beforeStreamOpens() {
        when(deliveryUseCase.checkTrackingOwnership(eq(NEED_ID), eq(NON_OWNER_UUID)))
                .thenReturn(Mono.error(new AccessDeniedException("This delivery need belongs to another user")));

        clientFor(NON_OWNER_UUID)
                .get().uri("/api/v1/deliveries/tracking/stream/delivery-need/{id}", NEED_ID)
                .exchange()
                .expectStatus().isForbidden();
    }

    /**
     * Unknown need → checkTrackingOwnership fails with IllegalArgumentException
     * ("not found") → GlobalExceptionHandler → 404.
     * Aligned with the REST endpoint (same error code for same condition).
     */
    @Test
    void trackByNeedStream_sse_unknownNeed_returns404() {
        when(deliveryUseCase.checkTrackingOwnership(eq(UNKNOWN_ID), eq(OWNER_UUID)))
                .thenReturn(Mono.error(new IllegalArgumentException("DeliveryNeed not found: " + UNKNOWN_ID)));

        clientFor(OWNER_UUID)
                .get().uri("/api/v1/deliveries/tracking/stream/delivery-need/{id}", UNKNOWN_ID)
                .exchange()
                .expectStatus().isNotFound();
    }

    static class FixedContextResolver implements HandlerMethodArgumentResolver {
        private final TntSecurityContext ctx;

        FixedContextResolver(TntSecurityContext ctx) { this.ctx = ctx; }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class)
                    && TntSecurityContext.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Mono<Object> resolveArgument(
                MethodParameter parameter,
                BindingContext bindingContext,
                ServerWebExchange exchange) {
            return Mono.justOrEmpty(ctx);
        }
    }
}
