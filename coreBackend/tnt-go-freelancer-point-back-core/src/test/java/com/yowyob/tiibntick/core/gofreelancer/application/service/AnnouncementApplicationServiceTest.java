package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.INegotiationChatPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.AnnouncementSubscriptionRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpDeliveryOtpProperties;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AnnouncementApplicationService}'s pricing branches after the Kernel
 * wallet migration: neither FIXED_PRICE (publish) nor QUOTE_REQUEST (select) escrows money any
 * more — a debit needs the client's own confirmation code, so the client pays the delivery
 * person from their Kernel wallet once the delivery is assigned (DeliveryPaymentApplicationService).
 *
 * @author MANFOUO BRAUN
 */
@ExtendWith(MockitoExtension.class)
class AnnouncementApplicationServiceTest {

    @Mock private IDeliveryAnnouncementPort deliveryAnnouncementPort;
    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private FreelancerQuotaService freelancerQuotaService;
    @Mock private INegotiationChatPort negotiationChatPort;
    @Mock private com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository gofpFreelancerRepository;
    @Mock private com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository gofpUserRepository;
    @Mock private com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository deliveryRepository;
    @Mock private DeliveryOtpService deliveryOtpService;
    @Mock private GofpDeliveryOtpProperties deliveryOtpProperties;
    @Mock private com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort lifecycle;

    private AnnouncementApplicationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID announcementId = UUID.randomUUID();
    private final UUID freelancerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AnnouncementApplicationService(
                deliveryAnnouncementPort, tenantContextHolder, freelancerQuotaService,
                negotiationChatPort,
                gofpFreelancerRepository, gofpUserRepository, deliveryRepository, deliveryOtpService,
                deliveryOtpProperties, lifecycle);
        com.yowyob.tiibntick.core.gofreelancer.TestDeliveries.acceptTerms(lifecycle);
        lenient().when(deliveryRepository.findById(any())).thenReturn(Mono.empty());
        lenient().when(gofpFreelancerRepository.findById(any())).thenReturn(Mono.empty());
        lenient().when(gofpFreelancerRepository.findByCoreFreelancerId(any())).thenReturn(Mono.empty());
    }

    @Test
    void createAnnouncement_fixedPrice_publishesWithoutAnyEscrow() {
        AnnouncementRequestDTO request = new AnnouncementRequestDTO();
        request.setClientId(clientId);
        request.setPricingMode("FIXED_PRICE");
        request.setAmount(5000.0);
        request.setCurrency("XAF");

        AnnouncementSnapshot published = snapshot(
                AnnouncementSnapshot.PRICING_MODE_FIXED_PRICE, BigDecimal.valueOf(5000), "XAF", List.of());

        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(deliveryAnnouncementPort.publish(any(PublishAnnouncementPortCommand.class)))
                .thenReturn(Mono.just(published));

        StepVerifier.create(service.createAnnouncement(request))
                .expectNextMatches(dto -> announcementId.equals(dto.getId()))
                .verifyComplete();
    }

    @Test
    void createAnnouncement_quoteRequest_publishesWithoutAnyEscrow() {
        AnnouncementRequestDTO request = new AnnouncementRequestDTO();
        request.setClientId(clientId);
        request.setPricingMode("QUOTE_REQUEST");
        request.setCurrency("XAF");

        AnnouncementSnapshot published = snapshot(
                AnnouncementSnapshot.PRICING_MODE_QUOTE_REQUEST, null, "XAF", List.of());

        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(deliveryAnnouncementPort.publish(any(PublishAnnouncementPortCommand.class)))
                .thenReturn(Mono.just(published));

        StepVerifier.create(service.createAnnouncement(request))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void assignResponse_quoteRequest_selectsWithoutEscrow() {
        UUID responseId = UUID.randomUUID();
        AnnouncementResponseSnapshot response = new AnnouncementResponseSnapshot(
                responseId, freelancerId, BigDecimal.valueOf(3500), "XAF", "SENT", Instant.now());
        AnnouncementSnapshot before = snapshot(
                AnnouncementSnapshot.PRICING_MODE_QUOTE_REQUEST, null, "XAF", List.of(response));
        AnnouncementSnapshot assigned = snapshot(
                AnnouncementSnapshot.PRICING_MODE_QUOTE_REQUEST, null, "XAF", List.of(response));

        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(deliveryAnnouncementPort.findById(tenantId, announcementId)).thenReturn(Mono.just(before));
        when(deliveryAnnouncementPort.selectResponse(tenantId, announcementId, clientId, responseId))
                .thenReturn(Mono.just(assigned));

        StepVerifier.create(service.assignResponse(announcementId, clientId, responseId))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void assignResponse_fixedPrice_selectsWithoutEscrow() {
        UUID responseId = UUID.randomUUID();
        AnnouncementResponseSnapshot response = new AnnouncementResponseSnapshot(
                responseId, freelancerId, null, null, "SENT", Instant.now());
        AnnouncementSnapshot before = snapshot(
                AnnouncementSnapshot.PRICING_MODE_FIXED_PRICE, BigDecimal.valueOf(5000), "XAF", List.of(response));
        AnnouncementSnapshot assigned = snapshot(
                AnnouncementSnapshot.PRICING_MODE_FIXED_PRICE, BigDecimal.valueOf(5000), "XAF", List.of(response));

        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(deliveryAnnouncementPort.findById(tenantId, announcementId)).thenReturn(Mono.just(before));
        when(deliveryAnnouncementPort.selectResponse(tenantId, announcementId, clientId, responseId))
                .thenReturn(Mono.just(assigned));

        StepVerifier.create(service.assignResponse(announcementId, clientId, responseId))
                .expectNextCount(1)
                .verifyComplete();

    }

    @Test
    void respondToAnnouncement_rejectsWhenFreelancerHasNoRemainingQuota() {
        var request = new com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RespondAnnouncementRequestDTO();
        request.setFreelancerId(freelancerId);

        when(freelancerQuotaService.hasRemainingQuota(freelancerId)).thenReturn(Mono.just(false));

        StepVerifier.create(service.respondToAnnouncement(announcementId, request))
                .expectError(IllegalStateException.class)
                .verify();

        verify(deliveryAnnouncementPort, never()).respond(any());
    }

    @Test
    void respondToAnnouncement_tellsTheSenderACandidateApplied_lotC36() {
        AnnouncementResponseSnapshot mine = new AnnouncementResponseSnapshot(UUID.randomUUID(), freelancerId,
                new BigDecimal("2500"), "XAF", "PENDING", Instant.now());
        AnnouncementSnapshot after = snapshot(AnnouncementSnapshot.PRICING_MODE_QUOTE_REQUEST, null, "XAF", List.of(mine));
        var request = new com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RespondAnnouncementRequestDTO();
        request.setFreelancerId(freelancerId);
        when(freelancerQuotaService.hasRemainingQuota(freelancerId)).thenReturn(Mono.just(true));
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(deliveryAnnouncementPort.hasResponse(announcementId, freelancerId)).thenReturn(Mono.just(false));
        when(deliveryAnnouncementPort.respond(any())).thenReturn(Mono.just(after));
        lenient().when(negotiationChatPort.openNegotiationThread(any(), any(), any())).thenReturn(Mono.empty());
        InAppEvents events = org.mockito.Mockito.mock(InAppEvents.class);
        when(events.candidateApplied(any(), any(), any())).thenReturn(Mono.empty());
        service.setInAppEvents(events);

        StepVerifier.create(service.respondToAnnouncement(announcementId, request)).expectNextCount(1).verifyComplete();

        verify(events, org.mockito.Mockito.times(1))
                .candidateApplied(announcementId, freelancerId, new BigDecimal("2500"));
    }

    @Test
    void respondToAnnouncement_idempotent_secondCallDoesNotInvokeRespond() {
        AnnouncementSnapshot current = snapshot(
                AnnouncementSnapshot.PRICING_MODE_FIXED_PRICE, null, "XAF", List.of());

        var request = new com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RespondAnnouncementRequestDTO();
        request.setFreelancerId(freelancerId);

        when(freelancerQuotaService.hasRemainingQuota(freelancerId)).thenReturn(Mono.just(true));
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        // hasResponse — the targeted point query — returns true (already responded).
        when(deliveryAnnouncementPort.hasResponse(announcementId, freelancerId))
                .thenReturn(Mono.just(true));
        // findById is used only on the idempotent return path to build the DTO.
        when(deliveryAnnouncementPort.findById(tenantId, announcementId)).thenReturn(Mono.just(current));

        StepVerifier.create(service.respondToAnnouncement(announcementId, request))
                .expectNextMatches(dto -> announcementId.equals(dto.getId()))
                .verifyComplete();

        // Core must not create a second candidature.
        verify(deliveryAnnouncementPort, never()).respond(any());
        // Core must not open a second negotiation thread.
        verify(negotiationChatPort, never()).openNegotiationThread(any(), any(), any());
    }

    @Test
    void respondToAnnouncement_idempotent_returnsExistingCandidatureNotError() {
        AnnouncementSnapshot current = snapshot(
                AnnouncementSnapshot.PRICING_MODE_QUOTE_REQUEST, null, "XAF", List.of());

        var request = new com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.RespondAnnouncementRequestDTO();
        request.setFreelancerId(freelancerId);

        when(freelancerQuotaService.hasRemainingQuota(freelancerId)).thenReturn(Mono.just(true));
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(deliveryAnnouncementPort.hasResponse(announcementId, freelancerId))
                .thenReturn(Mono.just(true));
        when(deliveryAnnouncementPort.findById(tenantId, announcementId)).thenReturn(Mono.just(current));

        // Must complete with the existing announcement data, not throw.
        StepVerifier.create(service.respondToAnnouncement(announcementId, request))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("M1: the price fails to be recorded, then selecting the same response again finishes the job without selecting twice")
    void assignResponse_priceFailsThenRetrySucceeds() {
        UUID deliveryId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        selectionCreates(deliveryId, responseId);
        when(lifecycle.recordAgreedTerms(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("delivery-core unavailable")))
                .thenAnswer(inv -> Mono.just(new com.yowyob.tiibntick.core.gofreelancer.application.port.out
                        .IDeliveryLifecyclePort.CoreDeliveryState(deliveryId, "CREATED", null, null, null, null)));
        when(deliveryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(deliveryOtpService.initOtpIfAbsentWithCodes(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.assignResponse(announcementId, clientId, responseId))
                .expectError(com.yowyob.tiibntick.core.gofreelancer.domain.exception.CodedStatusException.class)
                .verify();
        verify(deliveryRepository, never()).save(any());

        // The core now reports the announcement as assigned to that response, with its delivery.
        when(deliveryAnnouncementPort.findById(tenantId, announcementId)).thenReturn(Mono.just(assignedTo(deliveryId, responseId)));

        StepVerifier.create(service.assignResponse(announcementId, clientId, responseId))
                .expectNextCount(1).verifyComplete();

        verify(deliveryAnnouncementPort, org.mockito.Mockito.times(1))
                .selectResponse(tenantId, announcementId, clientId, responseId);
        verify(lifecycle, org.mockito.Mockito.times(2))
                .recordAgreedTerms(any(), any(), any(), any(), any(), any(), any(), any());
        verify(deliveryRepository).save(any());
        verify(deliveryOtpService).initOtpIfAbsentWithCodes(any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("M1: replay of a finished selection records nothing again (terms already in the core, gofp row present)")
    void assignResponse_replayOfFinishedSelection_isIdempotent() {
        UUID deliveryId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(deliveryAnnouncementPort.findById(tenantId, announcementId)).thenReturn(Mono.just(assignedTo(deliveryId, responseId)));
        when(lifecycle.findState(tenantId, deliveryId)).thenReturn(Mono.just(new com.yowyob.tiibntick.core.gofreelancer
                .application.port.out.IDeliveryLifecyclePort.CoreDeliveryState(deliveryId, "CREATED", null, null, null,
                null, BigDecimal.valueOf(5000), "XAF", Instant.now(), null, null, null, null, null, null)));
        com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery row = new com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery();
        row.setId(deliveryId);
        when(deliveryRepository.findById(deliveryId)).thenReturn(Mono.just(row));
        when(deliveryOtpService.initOtpIfAbsentWithCodes(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.assignResponse(announcementId, clientId, responseId))
                .expectNextCount(1).verifyComplete();

        verify(deliveryAnnouncementPort, never()).selectResponse(any(), any(), any(), any());
        verify(lifecycle, never()).recordAgreedTerms(any(), any(), any(), any(), any(), any(), any(), any());
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("M1: an announcement assigned to ANOTHER response is not treated as a replay")
    void alreadySelected_onlyForTheSameResponse() {
        UUID deliveryId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        assertThat(AnnouncementApplicationService.alreadySelected(assignedTo(deliveryId, responseId), responseId)).isTrue();
        assertThat(AnnouncementApplicationService.alreadySelected(assignedTo(deliveryId, responseId), UUID.randomUUID())).isFalse();
        assertThat(AnnouncementApplicationService.alreadySelected(assignedTo(null, responseId), responseId)).isFalse();
        assertThat(AnnouncementApplicationService.alreadySelected(snapshot(
                AnnouncementSnapshot.PRICING_MODE_FIXED_PRICE, BigDecimal.TEN, "XAF", List.of()), responseId)).isFalse();
    }

    /** The announcement as the core reports it once {@code responseId} is selected and its delivery created. */
    private AnnouncementSnapshot assignedTo(UUID deliveryId, UUID responseId) {
        AnnouncementResponseSnapshot response = new AnnouncementResponseSnapshot(
                responseId, freelancerId, null, null, "ACCEPTED", Instant.now());
        return new AnnouncementSnapshot(
                announcementId, tenantId, clientId, "Delivery announcement", "desc",
                AnnouncementStatus.ASSIGNED, Instant.now(), Instant.now(),
                BigDecimal.valueOf(5000), "XAF", AnnouncementSnapshot.PRICING_MODE_FIXED_PRICE,
                null, null, 0.0, null, null, null, null, null, null, null, null,
                "Jean Dupont", "+237600000000", responseId, deliveryId, "TNT-1", List.of(response));
    }

    /** Selection of a fixed-price response that creates core delivery {@code deliveryId}. */
    private void selectionCreates(UUID deliveryId, UUID responseId) {
        AnnouncementResponseSnapshot response = new AnnouncementResponseSnapshot(
                responseId, freelancerId, null, null, "SENT", Instant.now());
        AnnouncementSnapshot before = snapshot(
                AnnouncementSnapshot.PRICING_MODE_FIXED_PRICE, BigDecimal.valueOf(5000), "XAF", List.of(response));
        AnnouncementSnapshot assigned = new AnnouncementSnapshot(
                announcementId, tenantId, clientId, "Delivery announcement", "desc",
                AnnouncementStatus.ASSIGNED, Instant.now(), Instant.now(),
                BigDecimal.valueOf(5000), "XAF", AnnouncementSnapshot.PRICING_MODE_FIXED_PRICE,
                null, null, 0.0, null, null, null, null, null, null, null, null,
                "Jean Dupont", "+237600000000", responseId, deliveryId, "TNT-1", List.of(response));
        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(deliveryAnnouncementPort.findById(tenantId, announcementId)).thenReturn(Mono.just(before));
        when(deliveryAnnouncementPort.selectResponse(tenantId, announcementId, clientId, responseId))
                .thenReturn(Mono.just(assigned));
        when(deliveryRepository.findById(deliveryId)).thenReturn(Mono.empty());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("lot L1: the agreed price is recorded on the core delivery before the gofp row is written")
    void assignResponse_recordsTheAgreedPriceInTheCore() {
        UUID deliveryId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        selectionCreates(deliveryId, responseId);
        when(deliveryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(deliveryOtpService.initOtpIfAbsentWithCodes(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.assignResponse(announcementId, clientId, responseId))
                .expectNextCount(1).verifyComplete();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(lifecycle, deliveryRepository);
        order.verify(lifecycle).recordAgreedTerms(org.mockito.ArgumentMatchers.eq(tenantId),
                org.mockito.ArgumentMatchers.eq(deliveryId),
                org.mockito.ArgumentMatchers.argThat(p -> p != null && p.compareTo(BigDecimal.valueOf(5000)) == 0),
                org.mockito.ArgumentMatchers.eq("XAF"), any(), any(), any(), any());
        order.verify(deliveryRepository).save(any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("lot L1: if the core cannot record the price, the selection fails and no gofp row is written")
    void assignResponse_priceNotRecorded_failsTheSelection() {
        UUID deliveryId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        selectionCreates(deliveryId, responseId);
        when(lifecycle.recordAgreedTerms(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("delivery-core unavailable")));

        StepVerifier.create(service.assignResponse(announcementId, clientId, responseId))
                .expectErrorMatches(e -> e instanceof com.yowyob.tiibntick.core.gofreelancer.domain.exception.CodedStatusException c
                        && c.getStatusCode().value() == 503
                        && "AGREED_TERMS_NOT_RECORDED".equals(c.getCode())
                        && c.getReason().contains("Select the same response again"))
                .verify();

        org.mockito.Mockito.verify(deliveryRepository, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verify(deliveryOtpService, org.mockito.Mockito.never()).initOtpIfAbsentWithCodes(any());
    }

    private AnnouncementSnapshot snapshot(
            String pricingMode, BigDecimal offeredAmount, String currency,
            List<AnnouncementResponseSnapshot> responses) {
        return new AnnouncementSnapshot(
                announcementId, tenantId, clientId, "Delivery announcement", "desc",
                AnnouncementStatus.PUBLISHED, Instant.now(), Instant.now(),
                offeredAmount, currency, pricingMode,
                null, null, 0.0,
                null, null, null, null, null, null, null, null,
                "Jean Dupont", "+237600000000",
                null, null, null, responses);
    }
}
