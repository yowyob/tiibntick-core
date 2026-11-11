package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryProofService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryProofService.ProofView;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryProof.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Delivery proofs REST")
class GofpDeliveryProofControllerTest {

    private final DeliveryProofService service = mock(DeliveryProofService.class);
    private final UUID delivery = UUID.randomUUID();
    private final UUID caller = UUID.randomUUID();
    private final UUID file = UUID.randomUUID();

    private WebTestClient client() {
        HandlerMethodArgumentResolver resolver = new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter p) {
                return p.hasParameterAnnotation(CurrentUser.class) && TntSecurityContext.class.equals(p.getParameterType());
            }
            @Override public Mono<Object> resolveArgument(MethodParameter p, BindingContext b, ServerWebExchange ex) {
                String id = ex.getRequest().getHeaders().getFirst("X-Test-User");
                return id == null ? Mono.just(TntSecurityContext.anonymous())
                        : Mono.just(TntSecurityContext.builder().userId(UUID.fromString(id)).authenticated(true).build());
            }
        };
        return WebTestClient.bindToController(new GofpDeliveryProofController(service))
                .argumentResolvers(c -> c.addCustomResolver(resolver))
                .controllerAdvice(new Denied()).build();
    }

    @org.springframework.web.bind.annotation.RestControllerAdvice
    public static class Denied {
        @org.springframework.web.bind.annotation.ExceptionHandler(AccessDeniedException.class)
        public org.springframework.http.ResponseEntity<Void> denied() {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
    }

    private ProofView view() {
        return new ProofView(UUID.randomUUID(), delivery, "DELIVERY_PHOTO", file, "ok", Instant.parse("2030-01-01T10:00:00Z"), caller);
    }

    @Test
    @DisplayName("POST attaches a proof as the caller, whatever author the body claims")
    void post() {
        when(service.add(eq(delivery), eq(caller), any())).thenReturn(Mono.just(view()));
        client().post().uri("/api/v1/deliveries/{d}/proofs", delivery).header("X-Test-User", caller.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"kind\":\"DELIVERY_PHOTO\",\"fileId\":\"" + file + "\",\"note\":\"ok\",\"uploadedBy\":\"" + UUID.randomUUID() + "\"}")
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.fileId").isEqualTo(file.toString()).jsonPath("$.uploadedBy").isEqualTo(caller.toString());
        verify(service).add(eq(delivery), eq(caller), eq(new DeliveryProofService.NewProof(Kind.DELIVERY_PHOTO, file, "ok", null)));
    }

    @Test
    @DisplayName("GET lists the proofs for a party of the delivery")
    void get() {
        when(service.list(delivery, caller)).thenReturn(Flux.just(view()));
        client().get().uri("/api/v1/deliveries/{d}/proofs", delivery).header("X-Test-User", caller.toString())
                .exchange().expectStatus().isOk().expectBody().jsonPath("$[0].kind").isEqualTo("DELIVERY_PHOTO");
    }

    @Test
    @DisplayName("anonymous and refused callers get 403; an unknown delivery is 404")
    void errors() {
        client().get().uri("/api/v1/deliveries/{d}/proofs", delivery).exchange().expectStatus().isForbidden();
        client().post().uri("/api/v1/deliveries/{d}/proofs", delivery).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"kind\":\"OTHER\",\"fileId\":\"" + file + "\"}").exchange().expectStatus().isForbidden();

        when(service.list(delivery, caller)).thenReturn(Flux.error(new AccessDeniedException("no")));
        client().get().uri("/api/v1/deliveries/{d}/proofs", delivery).header("X-Test-User", caller.toString())
                .exchange().expectStatus().isForbidden();

        when(service.add(any(), any(), any())).thenReturn(Mono.error(new IllegalArgumentException("Delivery not found")));
        client().post().uri("/api/v1/deliveries/{d}/proofs", delivery).header("X-Test-User", caller.toString())
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"kind\":\"OTHER\",\"fileId\":\"" + file + "\"}")
                .exchange().expectStatus().isNotFound();
    }
}
