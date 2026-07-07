package com.yowyob.tiibntick.core.billing.templates.adapter.web;

import com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.BillingTemplatesExceptionHandler;
import com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.PolicyTemplateController;
import com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.ResponseMapper;
import com.yowyob.tiibntick.core.billing.templates.domain.exception.TemplateNotFoundException;
import com.yowyob.tiibntick.core.billing.templates.domain.model.*;
import com.yowyob.tiibntick.core.billing.templates.port.inbound.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.Mockito.when;

/**
 * Slice tests for {@link PolicyTemplateController}.
 * Uses a standalone WebFlux test setup to avoid loading the full application context.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@ExtendWith(MockitoExtension.class)
class PolicyTemplateControllerTest {

    @Mock private IListTemplatesUseCase listTemplatesUseCase;
    @Mock private IApplyTemplateUseCase applyTemplateUseCase;
    @Mock private IPreviewPriceUseCase previewPriceUseCase;
    @Mock private ISaveCustomTemplateUseCase saveCustomTemplateUseCase;
    @Mock private ICreateAdminTemplateUseCase createAdminTemplateUseCase;

    private WebTestClient webTestClient;
    private PolicyTemplate sampleTemplate;

    @BeforeEach
    void setUp() {
        PolicyTemplateController controller = new PolicyTemplateController(
                listTemplatesUseCase, applyTemplateUseCase, previewPriceUseCase,
                saveCustomTemplateUseCase, createAdminTemplateUseCase,
                new ResponseMapper()
        );

        webTestClient = WebTestClient
                .bindToController(controller)
                .controllerAdvice(new BillingTemplatesExceptionHandler())
                .build();

        sampleTemplate = PolicyTemplate.createNew(
                "TPL-BASE-STD", "Standard Base Pricing", "Description",
                TemplateCategory.BASE,
                List.of(PolicyOwnerType.AGENCY, PolicyOwnerType.FREELANCER_ORG),
                List.of(),
                "IF weight >= 0 THEN SET_BASE(500 XAF)"
        );
    }

    @Test
    @DisplayName("GET /api/v1/billing/templates?ownerType=AGENCY should return 200 with templates")
    void shouldListTemplatesForOwnerType() {
        when(listTemplatesUseCase.listForOwnerType(PolicyOwnerType.AGENCY))
                .thenReturn(Flux.just(sampleTemplate));

        webTestClient.get()
                .uri("/api/v1/billing/templates?ownerType=AGENCY")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class)
                .hasSize(1);
    }

    @Test
    @DisplayName("GET /api/v1/billing/templates/{code} should return 200 when found")
    void shouldGetTemplateByCode() {
        when(listTemplatesUseCase.getByCode("TPL-BASE-STD")).thenReturn(Mono.just(sampleTemplate));

        webTestClient.get()
                .uri("/api/v1/billing/templates/TPL-BASE-STD")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.templateCode").isEqualTo("TPL-BASE-STD");
    }

    @Test
    @DisplayName("GET /api/v1/billing/templates/{code} should return 404 when not found")
    void shouldReturn404WhenTemplateNotFound() {
        when(listTemplatesUseCase.getByCode("TPL-UNKNOWN"))
                .thenReturn(Mono.error(new TemplateNotFoundException("TPL-UNKNOWN")));

        webTestClient.get()
                .uri("/api/v1/billing/templates/TPL-UNKNOWN")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    @DisplayName("GET /api/v1/billing/templates/admin/all should return all templates")
    void shouldListAllTemplatesForAdmin() {
        when(listTemplatesUseCase.listAll()).thenReturn(Flux.just(sampleTemplate));

        webTestClient.get()
                .uri("/api/v1/billing/templates/admin/all")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class)
                .hasSize(1);
    }
}
