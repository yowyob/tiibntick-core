package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.HandoverCodesDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.service.HandoverCodeService;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.CodedStatusException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** C-53 — HTTP shape of the owner route: 200 no-store, 403, 404, 409 with code NO_COURIER_YET. */
@DisplayName("C-53 — GET /api/delivery-needs/{id}/handover-codes")
class HandoverCodeWebTest {

    private final UUID needId = UUID.randomUUID();
    private final UUID caller = UUID.randomUUID();
    private final HandoverCodeService service = mock(HandoverCodeService.class);

    private WebTestClient client() {
        TntSecurityContext ctx = TntSecurityContext.builder().userId(caller).build();
        return WebTestClient.bindToController(new HandoverCodeController(service))
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new DeliveryNeedControllerWebTest.FixedContextResolver(ctx)))
                .build();
    }

    @Test
    void owner_200_noStore() {
        when(service.forOwner(needId, caller)).thenReturn(Mono.just(new HandoverCodesDTO("123456", "654321", "CREATED")));
        client().get().uri("/api/delivery-needs/{id}/handover-codes", needId).exchange()
                .expectStatus().isOk()
                .expectHeader().valueMatches("Cache-Control", ".*no-store.*")
                .expectBody()
                .jsonPath("$.pickupCode").isEqualTo("123456")
                .jsonPath("$.deliveryCode").isEqualTo("654321")
                .jsonPath("$.deliveryStatus").isEqualTo("CREATED");
    }

    @Test
    void notOwner_403() {
        when(service.forOwner(needId, caller)).thenReturn(Mono.error(new AccessDeniedException("no")));
        client().get().uri("/api/delivery-needs/{id}/handover-codes", needId).exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void unknown_404() {
        when(service.forOwner(needId, caller)).thenReturn(Mono.error(new IllegalArgumentException("DeliveryNeed not found: x")));
        client().get().uri("/api/delivery-needs/{id}/handover-codes", needId).exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void noCourier_409_withCode() {
        when(service.forOwner(needId, caller)).thenReturn(Mono.error(
                new CodedStatusException(HttpStatus.CONFLICT, "NO_COURIER_YET", "No courier yet")));
        client().get().uri("/api/delivery-needs/{id}/handover-codes", needId).exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("NO_COURIER_YET");
    }
}
