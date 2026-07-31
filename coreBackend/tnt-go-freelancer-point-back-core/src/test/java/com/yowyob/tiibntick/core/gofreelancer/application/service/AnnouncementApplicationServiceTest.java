package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.DebitWalletCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.Money;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletTransaction;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.INegotiationChatPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.AnnouncementSubscriptionRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AnnouncementApplicationService}'s escrow branches — the module's
 * biggest correctness risk given it had zero test coverage (see
 * {@code docs/knowledge/known-issues.md} #23) despite handling real money movement.
 *
 * <p>Covers both product experiences from {@code architecture/decisions.md} ADR-021:
 * FIXED_PRICE escrows at publish time, QUOTE_REQUEST escrows at selection time using the
 * chosen response's own proposed price — and that neither path double-charges the other.
 *
 * @author MANFOUO BRAUN
 */
@ExtendWith(MockitoExtension.class)
class AnnouncementApplicationServiceTest {

    @Mock private IDeliveryAnnouncementPort deliveryAnnouncementPort;
    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private FreelancerQuotaService freelancerQuotaService;
    @Mock private IWalletUseCase walletUseCase;
    @Mock private INegotiationChatPort negotiationChatPort;
    @Mock private IAnnouncementRepository announcementRepository;
    @Mock private AnnouncementSubscriptionRepository subscriptionRepository;

    private AnnouncementApplicationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();
    private final UUID announcementId = UUID.randomUUID();
    private final UUID freelancerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AnnouncementApplicationService(
                deliveryAnnouncementPort, tenantContextHolder, freelancerQuotaService,
                walletUseCase, negotiationChatPort, announcementRepository, subscriptionRepository);
    }

    @Test
    void createAnnouncement_fixedPrice_debitsWalletEscrowAtPublish() {
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
        when(walletUseCase.debitWallet(any())).thenReturn(Mono.just(WalletTransaction.builder().build()));
        when(announcementRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.createAnnouncement(request))
                .expectNextMatches(dto -> announcementId.equals(dto.getId()))
                .verifyComplete();

        ArgumentCaptor<DebitWalletCommand> captor = ArgumentCaptor.forClass(DebitWalletCommand.class);
        verify(walletUseCase).debitWallet(captor.capture());
        assertThat(captor.getValue().amount()).isEqualTo(Money.of(BigDecimal.valueOf(5000), "XAF"));
        assertThat(captor.getValue().userId()).isEqualTo(clientId);
        assertThat(captor.getValue().tenantId()).isEqualTo(tenantId);
    }

    @Test
    void createAnnouncement_quoteRequest_doesNotDebitWalletAtPublish() {
        AnnouncementRequestDTO request = new AnnouncementRequestDTO();
        request.setClientId(clientId);
        request.setPricingMode("QUOTE_REQUEST");
        request.setCurrency("XAF");

        AnnouncementSnapshot published = snapshot(
                AnnouncementSnapshot.PRICING_MODE_QUOTE_REQUEST, null, "XAF", List.of());

        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(deliveryAnnouncementPort.publish(any(PublishAnnouncementPortCommand.class)))
                .thenReturn(Mono.just(published));
        when(announcementRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.createAnnouncement(request))
                .expectNextCount(1)
                .verifyComplete();

        verify(walletUseCase, never()).debitWallet(any());
    }

    @Test
    void assignResponse_quoteRequest_resolvesEscrowAmountFromPortAndDebitsProposedPrice() {
        UUID responseId = UUID.randomUUID();
        AnnouncementResponseSnapshot response = new AnnouncementResponseSnapshot(
                responseId, freelancerId, BigDecimal.valueOf(3500), "XAF", "SENT", Instant.now());
        AnnouncementSnapshot before = snapshot(
                AnnouncementSnapshot.PRICING_MODE_QUOTE_REQUEST, null, "XAF", List.of(response));
        AnnouncementSnapshot assigned = snapshot(
                AnnouncementSnapshot.PRICING_MODE_QUOTE_REQUEST, null, "XAF", List.of(response));

        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(deliveryAnnouncementPort.findById(tenantId, announcementId)).thenReturn(Mono.just(before));
        when(deliveryAnnouncementPort.resolveEscrowAmount(tenantId, announcementId, responseId))
                .thenReturn(Mono.just(BigDecimal.valueOf(3500)));
        when(deliveryAnnouncementPort.selectResponse(tenantId, announcementId, clientId, responseId))
                .thenReturn(Mono.just(assigned));
        when(walletUseCase.debitWallet(any())).thenReturn(Mono.just(WalletTransaction.builder().build()));
        when(announcementRepository.findById(announcementId)).thenReturn(Mono.empty());

        StepVerifier.create(service.assignResponse(announcementId, clientId, responseId))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<DebitWalletCommand> captor = ArgumentCaptor.forClass(DebitWalletCommand.class);
        verify(walletUseCase).debitWallet(captor.capture());
        assertThat(captor.getValue().amount()).isEqualTo(Money.of(BigDecimal.valueOf(3500), "XAF"));
    }

    @Test
    void assignResponse_fixedPrice_doesNotDebitWalletAgainAtSelect() {
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
        when(announcementRepository.findById(announcementId)).thenReturn(Mono.empty());

        StepVerifier.create(service.assignResponse(announcementId, clientId, responseId))
                .expectNextCount(1)
                .verifyComplete();

        verify(walletUseCase, never()).debitWallet(any());
        verify(deliveryAnnouncementPort, never()).resolveEscrowAmount(any(), any(), any());
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

    private AnnouncementSnapshot snapshot(
            String pricingMode, BigDecimal offeredAmount, String currency,
            List<AnnouncementResponseSnapshot> responses) {
        return new AnnouncementSnapshot(
                announcementId, tenantId, clientId, "Delivery announcement", "desc",
                AnnouncementStatus.PUBLISHED, Instant.now(), Instant.now(),
                offeredAmount, currency, pricingMode,
                null, null, 0.0, "Jean Dupont", "+237600000000",
                null, responses);
    }
}
