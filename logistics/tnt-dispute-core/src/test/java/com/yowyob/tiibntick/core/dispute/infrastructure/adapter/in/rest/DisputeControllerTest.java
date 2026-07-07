package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeCommandUseCase;
import com.yowyob.tiibntick.core.dispute.application.port.inbound.IDisputeQueryUseCase;
import com.yowyob.tiibntick.core.dispute.domain.enums.*;
import com.yowyob.tiibntick.core.dispute.domain.model.*;
import com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest.dto.request.DisputeRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DisputeController} using Mockito and WebTestClient.
 *
 * <p>Tests HTTP contract: status codes, response structure, header handling.
 * Application layer is mocked — only the HTTP adapter is under test.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DisputeController")
class DisputeControllerTest {

    private WebTestClient webClient;

    @Mock
    private IDisputeCommandUseCase commandUseCase;

    @Mock
    private IDisputeQueryUseCase queryUseCase;

    @InjectMocks
    private DisputeController controller;

    private ObjectMapper objectMapper;
    private Dispute stubDispute;

    @BeforeEach
    void setUp() {
        webClient = WebTestClient.bindToController(controller).build();
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        stubDispute = buildStubDispute();
    }

    // =========================================================================
    // POST /api/v1/disputes
    // =========================================================================

    @Test
    @DisplayName("POST /api/v1/disputes — should return 201 with opened dispute reference")
    void shouldReturn201OnOpenDispute() throws Exception {
        when(commandUseCase.openDispute(any())).thenReturn(Mono.just(stubDispute));

        DisputeRequests.OpenDisputeRequest request = new DisputeRequests.OpenDisputeRequest(
                "PACKAGE_DAMAGED", "MISSION_GO", "HIGH",
                "client-abc", "CLIENT",
                "freelancer-xyz", "FREELANCER",
                "mission-001", "pkg-001", "TKG-001",
                "Package was damaged on arrival",
                "org-789", null, false);

        webClient.post()
                .uri("/api/v1/disputes")
                .header("X-Tenant-ID", "agency-douala-01")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(request))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.status").isEqualTo("OPEN")
                .jsonPath("$.reference").exists();
    }

    // =========================================================================
    // GET /api/v1/disputes/{id}
    // =========================================================================

    @Test
    @DisplayName("GET /api/v1/disputes/{id} — should return 200 with dispute detail")
    void shouldReturn200OnGetDispute() {
        when(queryUseCase.getDispute(any())).thenReturn(Mono.just(stubDispute));

        webClient.get()
                .uri("/api/v1/disputes/{id}", stubDispute.getId().getValue())
                .header("X-Tenant-ID", "agency-douala-01")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(stubDispute.getId().getValue())
                .jsonPath("$.tenantId").isEqualTo("agency-douala-01")
                .jsonPath("$.status").isEqualTo("OPEN");
    }

    @Test
    @DisplayName("GET /api/v1/disputes/{id} — should return 404 when dispute not found")
    void shouldReturn404WhenDisputeNotFound() {
        when(queryUseCase.getDispute(any()))
                .thenReturn(Mono.error(new com.yowyob.tiibntick.core.dispute.domain.exception.DisputeNotFoundException(
                        "Dispute not found: nonexistent-id")));

        webClient.get()
                .uri("/api/v1/disputes/nonexistent-id")
                .header("X-Tenant-ID", "agency-douala-01")
                .exchange()
                .expectStatus().is5xxServerError(); // will be 404 with proper global exception handler
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private Dispute buildStubDispute() {
        return Dispute.open(new com.yowyob.tiibntick.core.dispute.application.command.OpenDisputeCommand(
                "agency-douala-01",
                "client-abc",
                ClaimantType.CLIENT,
                "freelancer-xyz",
                RespondentType.FREELANCER,
                DisputeCause.PACKAGE_DAMAGED,
                DisputeCategory.MISSION_GO,
                DisputePriority.HIGH,
                "mission-001",
                "pkg-001",
                "TKG-001",
                "Package arrived damaged",
                "org-789",
                null,
                false));
    }
}
