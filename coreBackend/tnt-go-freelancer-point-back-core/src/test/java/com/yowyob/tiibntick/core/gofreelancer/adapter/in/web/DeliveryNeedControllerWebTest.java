package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryNeedUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ResourceNotFoundException;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Traversal test: proves that {@code @CurrentUser} correctly propagates
 * {@code callerId} from the HTTP layer to the service, and that swapping to
 * {@code @AuthenticationPrincipal} silently injects {@code null} instead.
 *
 * <h3>Choice of WebTestClient.bindToController over @WebFluxTest</h3>
 * This test uses {@code WebTestClient.bindToController} (pattern from
 * {@code NetworkNodeControllerTest}) rather than {@code @WebFluxTest} because:
 * <ul>
 *   <li>It gives exact control over which argument resolver is registered — this
 *       is exactly the thing being tested.</li>
 *   <li>It avoids Spring context startup and avoids pulling in security auto-config
 *       ({@code GofpSecurityConfig}, JWT converters) that are not relevant here.</li>
 *   <li>The {@code FixedContextResolver} inner class explicitly models the contract:
 *       it handles {@code @CurrentUser} parameters and injects the configured
 *       {@code TntSecurityContext}.</li>
 * </ul>
 *
 * <h3>Regression proof</h3>
 * Replacing {@code @CurrentUser(required=false)} with {@code @AuthenticationPrincipal}
 * in {@link DeliveryNeedController#getCandidatesWithPricing} causes the
 * {@code FixedContextResolver} to be bypassed (it only supports {@code @CurrentUser}).
 * Spring's built-in {@code ReactiveAuthenticationPrincipalArgumentResolver} then
 * handles the parameter; with no security context in this test harness, it injects
 * {@code null}. The service stub for {@code (NEED_ID, null)} returns an empty Flux
 * → HTTP 200. The test that expects 403 therefore fails: the guard was bypassed.
 *
 * <h3>Three cases</h3>
 * <ol>
 *   <li>Owner's {@code sub} → service called with owner UUID → 200.</li>
 *   <li>Non-owner's {@code sub} → service called with non-owner UUID →
 *       {@link AccessDeniedException} → 403.</li>
 *   <li>Unknown need → service throws {@link ResourceNotFoundException} → 404,
 *       never 403 (existence must not be inferred from the error code).</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class DeliveryNeedControllerWebTest {

    static final UUID NEED_ID        = UUID.fromString("11111111-0000-0000-0000-000000000001");
    static final UUID UNKNOWN_ID     = UUID.fromString("00000000-0000-0000-0000-000000000000");
    static final UUID OWNER_UUID     = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    static final UUID NON_OWNER_UUID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    @Mock DeliveryNeedUseCase deliveryNeedUseCase;

    private WebTestClient clientFor(UUID callerId) {
        TntSecurityContext ctx = callerId != null
                ? TntSecurityContext.builder().userId(callerId).build()
                : TntSecurityContext.anonymous();

        DeliveryNeedController controller = new DeliveryNeedController(deliveryNeedUseCase);
        return WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new FixedContextResolver(ctx)))
                .build();
    }

    /**
     * Case 1 — owner gets candidates (200).
     * Service is called with (NEED_ID, OWNER_UUID) and returns an empty list.
     */
    @Test
    void getCandidates_owner_returns200() {
        when(deliveryNeedUseCase.getCandidatesWithPricing(eq(NEED_ID), eq(OWNER_UUID)))
                .thenReturn(Flux.empty());

        clientFor(OWNER_UUID)
                .get().uri("/api/delivery-needs/{id}/candidates", NEED_ID)
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * Case 2 — non-owner gets 403 (the ownership guard mord).
     *
     * <p><strong>Regression invariant:</strong> this test MUST fail when
     * {@code @CurrentUser(required=false)} is replaced with
     * {@code @AuthenticationPrincipal} in the controller parameter. Under
     * {@code @AuthenticationPrincipal}, {@code FixedContextResolver.supportsParameter}
     * returns {@code false} (it checks for {@code @CurrentUser}); Spring's built-in
     * resolver injects {@code null}; the service is called with null callerId → stub
     * returns Flux.empty() → 200, not 403 → this test fails.
     */
    @Test
    void getCandidates_nonOwner_returns403() {
        // null callerId: guard bypassed (demonstrates the @AuthenticationPrincipal bug —
        // only consumed when @AuthenticationPrincipal is used instead of @CurrentUser)
        lenient().when(deliveryNeedUseCase.getCandidatesWithPricing(eq(NEED_ID), isNull()))
                .thenReturn(Flux.empty());
        // non-owner callerId: service enforces ownership
        when(deliveryNeedUseCase.getCandidatesWithPricing(eq(NEED_ID), eq(NON_OWNER_UUID)))
                .thenReturn(Flux.error(new AccessDeniedException("This delivery need belongs to another user")));

        clientFor(NON_OWNER_UUID)
                .get().uri("/api/delivery-needs/{id}/candidates", NEED_ID)
                .exchange()
                .expectStatus().isForbidden();
    }

    /**
     * Case 3 — unknown need returns 404, never 403.
     * Ordering: the service resolves existence (404) before ownership (403).
     * A 403 on an absent resource would leak information about its existence.
     */
    @Test
    void getCandidates_unknownNeed_returns404() {
        when(deliveryNeedUseCase.getCandidatesWithPricing(eq(UNKNOWN_ID), eq(OWNER_UUID)))
                .thenReturn(Flux.error(new ResourceNotFoundException("Delivery need not found: " + UNKNOWN_ID)));

        clientFor(OWNER_UUID)
                .get().uri("/api/delivery-needs/{id}/candidates", UNKNOWN_ID)
                .exchange()
                .expectStatus().isNotFound();
    }

    /**
     * Argument resolver that injects a fixed {@link TntSecurityContext} for
     * parameters annotated with {@link CurrentUser}.
     *
     * <p>When the controller uses {@code @AuthenticationPrincipal} instead of
     * {@code @CurrentUser}, {@link #supportsParameter} returns {@code false},
     * this resolver is NOT invoked, and Spring's built-in principal resolver
     * injects {@code null} (no security context in this test harness).
     */
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
        public Mono<Object> resolveArgument(
                MethodParameter parameter,
                BindingContext bindingContext,
                ServerWebExchange exchange) {
            return Mono.justOrEmpty(ctx);
        }
    }
}
