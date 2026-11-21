package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.service.DeliveryStatusApplicationService;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.InvalidHandoverCodeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** C-53 — a wrong handover code answers 400 with code INVALID_HANDOVER_CODE; other bad requests keep no code. */
@DisplayName("C-53 — wrong handover code on PATCH /status")
class InvalidHandoverCodeWebTest {

    private final UUID deliveryId = UUID.randomUUID();
    private final UUID courier = UUID.randomUUID();
    private final DeliveryStatusApplicationService status = mock(DeliveryStatusApplicationService.class);

    private WebTestClient client() {
        TntSecurityContext ctx = TntSecurityContext.builder().userId(courier).build();
        return WebTestClient.bindToController(new GofpDeliveryController(mock(DeliveryUseCase.class), status))
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(
                        new GofpDeliveryControllerOwnershipTest.FixedContextResolver(ctx)))
                .build();
    }

    private WebTestClient.ResponseSpec patch() {
        return client().patch().uri("/api/v1/deliveries/{id}/status", deliveryId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"status\":\"PICKED_UP\",\"confirmationCode\":\"000000\"}")
                .exchange();
    }

    @Test
    void wrongCode_400_withStableCode() {
        when(status.assertCallerIsAssignedDeliveryPerson(eq(deliveryId), eq(courier))).thenReturn(Mono.empty());
        when(status.updateStatus(eq(deliveryId), any())).thenReturn(Mono.error(
                new InvalidHandoverCodeException("Code de confirmation incorrect.")));
        patch().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("INVALID_HANDOVER_CODE");
    }

    @Test
    void otherBadRequest_400_withoutCode() {
        when(status.assertCallerIsAssignedDeliveryPerson(eq(deliveryId), eq(courier))).thenReturn(Mono.empty());
        when(status.updateStatus(eq(deliveryId), any())).thenReturn(Mono.error(
                new IllegalArgumentException("relayPointId is required")));
        patch().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").doesNotExist();
    }
}
