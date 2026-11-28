package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RespondAnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AnnouncementUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.service.AnnouncementAccessGuard;
import com.yowyob.tiibntick.core.gofreelancer.application.service.FreelancerWhereaboutsResolver;
import com.yowyob.tiibntick.core.gofreelancer.application.service.TenantContextHolder;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Lot C-46 — announcements: identities come from the caller, only the client who published an
 * announcement (or staff) may choose its courier, cancel or change it; a courier may only take a
 * fixed-price announcement for themself. Real {@link AnnouncementAccessGuard}, mocked ports.
 */
@DisplayName("AnnouncementController — owner checks and caller identities")
class AnnouncementControllerAccessTest {

    static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID ANN = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000046");
    static final UUID OWNER = UUID.fromString("11111111-0000-0000-0000-000000000046");
    static final UUID STRANGER = UUID.fromString("33333333-0000-0000-0000-000000000046");
    static final UUID COURIER_USER = UUID.fromString("44444444-0000-0000-0000-000000000046");
    static final UUID COURIER_PROFILE = UUID.fromString("55555555-0000-0000-0000-000000000046");
    static final UUID OTHER_PROFILE = UUID.fromString("66666666-0000-0000-0000-000000000046");
    static final UUID RESPONSE = UUID.fromString("77777777-0000-0000-0000-000000000046");

    private final AnnouncementUseCase useCase = mock(AnnouncementUseCase.class);
    private final IDeliveryAnnouncementPort port = mock(IDeliveryAnnouncementPort.class);
    private final TenantContextHolder tenant = mock(TenantContextHolder.class);
    private final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);

    @BeforeEach
    void setUp() {
        when(tenant.currentTenantId()).thenReturn(Mono.just(TENANT));
        when(freelancers.findByCoreUserId(any())).thenReturn(Mono.empty());
        when(freelancers.findByCoreUserId(COURIER_USER)).thenReturn(Mono.just(GofpFreelancer.builder()
                .id(COURIER_PROFILE).coreFreelancerId(COURIER_PROFILE).coreUserId(COURIER_USER).build()));
        announcement("QUOTE_REQUEST");
        AnnouncementResponseDTO dto = new AnnouncementResponseDTO();
        when(useCase.assignResponse(any(), any(), any())).thenReturn(Mono.just(dto));
        when(useCase.assignFreelancer(any(), any())).thenReturn(Mono.just(dto));
        when(useCase.deleteAnnouncement(any())).thenReturn(Mono.empty());
        when(useCase.updateAnnouncement(any(), any())).thenReturn(Mono.just(dto));
        when(useCase.createAnnouncement(any())).thenReturn(Mono.just(dto));
        when(useCase.respondToAnnouncement(any(), any())).thenReturn(Mono.just(dto));
    }

    private void announcement(String pricingMode) {
        AnnouncementResponseSnapshot response = new AnnouncementResponseSnapshot(RESPONSE, OTHER_PROFILE,
                new BigDecimal("2000"), "XAF", "SENT", Instant.now());
        when(port.findById(TENANT, ANN)).thenReturn(Mono.just(new AnnouncementSnapshot(
                ANN, TENANT, OWNER, "Colis", "d", AnnouncementStatus.PUBLISHED, Instant.now(), Instant.now(),
                new BigDecimal("2000"), "XAF", pricingMode, Address.builder().street("Rue 1").city("Douala").country("CM").build(),
                Address.builder().street("Rue 1").city("Douala").country("CM").build(), 0.0, null, null, null, null, null, null, null, null,
                "Dest", "+237600000000", null, null, null, List.of(response))));
    }

    private WebTestClient as(UUID userId, String... roles) {
        TntSecurityContext ctx = TntSecurityContext.builder().userId(userId).tenantId(TENANT).authenticated(true)
                .roles(Set.of(roles)).build();
        AnnouncementController controller = new AnnouncementController(useCase,
                mock(FreelancerWhereaboutsResolver.class), new AnnouncementAccessGuard(port, tenant, freelancers));
        return WebTestClient.bindToController(controller)
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(new FixedContextResolver(ctx)))
                .build();
    }

    private WebTestClient.ResponseSpec assign(WebTestClient c, String body) {
        return c.post().uri("/api/announcements/{id}/assign", ANN).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body).exchange();
    }

    @Test
    @DisplayName("a stranger cannot choose the courier of someone else's announcement")
    void strangerCannotElect() {
        assign(as(STRANGER, "ROLE_CLIENT"), "{\"clientId\":\"" + STRANGER + "\",\"responseId\":\"" + RESPONSE + "\"}")
                .expectStatus().isForbidden();
        assign(as(STRANGER, "ROLE_CLIENT"), "{\"freelancerId\":\"" + OTHER_PROFILE + "\"}").expectStatus().isForbidden();
        verify(useCase, never()).assignResponse(any(), any(), any());
        verify(useCase, never()).assignFreelancer(any(), any());
    }

    @Test
    @DisplayName("a courier cannot elect themself on a quote request: the client chooses")
    void courierCannotSelfElectOnQuoteRequest() {
        assign(as(COURIER_USER, "ROLE_FREELANCER"), "{\"freelancerId\":\"" + COURIER_PROFILE + "\"}")
                .expectStatus().isForbidden();
        verify(useCase, never()).assignFreelancer(any(), any());
    }

    @Test
    @DisplayName("a courier takes a fixed-price announcement for themself (the app's accept), never for another")
    void courierTakesFixedPriceForThemselfOnly() {
        announcement("FIXED_PRICE");
        assign(as(COURIER_USER, "ROLE_FREELANCER"), "{\"freelancerId\":\"" + COURIER_PROFILE + "\"}")
                .expectStatus().isOk();
        assign(as(COURIER_USER, "ROLE_FREELANCER"), "{\"freelancerId\":\"" + OTHER_PROFILE + "\"}")
                .expectStatus().isForbidden();
        verify(useCase, times(1)).assignFreelancer(ANN, COURIER_PROFILE);
    }

    @Test
    @DisplayName("the owner elects; the client id is the announcement's, never the body's")
    void ownerElectsWithAnnouncementClientId() {
        assign(as(OWNER, "ROLE_CLIENT"), "{\"clientId\":\"" + STRANGER + "\",\"responseId\":\"" + RESPONSE + "\"}")
                .expectStatus().isOk();
        verify(useCase).assignResponse(ANN, OWNER, RESPONSE);
    }

    @Test
    @DisplayName("only the owner or staff cancel or change an announcement")
    void cancelAndUpdateOwnerOnly() {
        as(STRANGER, "ROLE_CLIENT").delete().uri("/api/announcements/{id}", ANN).exchange().expectStatus().isForbidden();
        as(STRANGER, "ROLE_FREELANCER").put().uri("/api/announcements/{id}", ANN).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"paymentMethod\":\"CASH\"}").exchange().expectStatus().isForbidden();
        verify(useCase, never()).deleteAnnouncement(any());
        verify(useCase, never()).updateAnnouncement(any(), any());

        as(OWNER, "ROLE_CLIENT").delete().uri("/api/announcements/{id}", ANN).exchange().expectStatus().isNoContent();
        as(STRANGER, "ROLE_SUPPORT_AGENT").delete().uri("/api/announcements/{id}", ANN).exchange()
                .expectStatus().isNoContent();
        verify(useCase, times(2)).deleteAnnouncement(ANN);
    }

    @Test
    @DisplayName("an announcement is published for the caller; another client id in the body is refused")
    void createUsesCallerIdentity() {
        as(OWNER, "ROLE_CLIENT").post().uri("/api/announcements").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"clientId\":\"" + STRANGER + "\",\"title\":\"x\"}").exchange().expectStatus().isForbidden();
        verify(useCase, never()).createAnnouncement(any());

        as(OWNER, "ROLE_CLIENT").post().uri("/api/announcements").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"x\"}").exchange().expectStatus().isCreated();
        ArgumentCaptor<AnnouncementRequestDTO> created = ArgumentCaptor.forClass(AnnouncementRequestDTO.class);
        verify(useCase).createAnnouncement(created.capture());
        assertThat(created.getValue().getClientId()).isEqualTo(OWNER);
    }

    @Test
    @DisplayName("a courier answers only as themself; another courier id in the body is refused")
    void respondUsesCallerIdentity() {
        as(COURIER_USER, "ROLE_FREELANCER").post().uri("/api/announcements/{id}/respond", ANN)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"freelancerId\":\"" + OTHER_PROFILE + "\"}")
                .exchange().expectStatus().isForbidden();
        as(STRANGER, "ROLE_CLIENT").post().uri("/api/announcements/{id}/subscribe", ANN)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isForbidden();
        verify(useCase, never()).respondToAnnouncement(any(), any());

        as(COURIER_USER, "ROLE_FREELANCER").post().uri("/api/announcements/{id}/respond", ANN)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"proposedPrice\":1500}")
                .exchange().expectStatus().isCreated();
        ArgumentCaptor<RespondAnnouncementRequestDTO> answered = ArgumentCaptor.forClass(RespondAnnouncementRequestDTO.class);
        verify(useCase).respondToAnnouncement(eq(ANN), answered.capture());
        assertThat(answered.getValue().getFreelancerId()).isEqualTo(COURIER_PROFILE);
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
