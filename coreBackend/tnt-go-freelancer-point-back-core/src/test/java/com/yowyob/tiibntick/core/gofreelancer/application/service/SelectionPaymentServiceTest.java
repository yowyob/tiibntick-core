package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.ICashCollectionUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IDebtUseCase;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletDebt;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ResponsibleParty;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.AnnouncementDetails;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IEscrowUseCase;
import com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.CashCollectionMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowShares;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AnnouncementUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.CommissionRatePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Announcement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("Lot L5: paid selection — intent, hold, then election with the escrowed delivery id")
class SelectionPaymentServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();
    private static final UUID COURIER = UUID.randomUUID();
    private static final UUID PERSON = UUID.randomUUID();
    private static final UUID ANNOUNCEMENT = UUID.randomUUID();
    private static final UUID RESPONSE = UUID.randomUUID();

    private final IDeliveryAnnouncementPort announcements = mock(IDeliveryAnnouncementPort.class);
    private final IAnnouncementRepository gofpAnnouncements = mock(IAnnouncementRepository.class);
    private final AnnouncementUseCase announcementUseCase = mock(AnnouncementUseCase.class);
    private final DeliveryQueryUseCase deliveryQuery = mock(DeliveryQueryUseCase.class);
    private final IEscrowUseCase escrows = mock(IEscrowUseCase.class);
    private final ICashCollectionUseCase cash = mock(ICashCollectionUseCase.class);
    private final IDebtUseCase debts = mock(IDebtUseCase.class);
    private final TenantContextHolder tenants = mock(TenantContextHolder.class);
    private SelectionPaymentService service;
    private final AnnouncementSnapshot open = mock(AnnouncementSnapshot.class);

    @BeforeEach
    void setUp() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("rates", (CommissionRatePort) (t, d) -> Mono.just(new BigDecimal("0.10")));
        beans.addBean("debts", debts);
        service = new SelectionPaymentService(announcements, gofpAnnouncements, announcementUseCase, deliveryQuery,
                escrows, cash, tenants, beans.getBeanProvider(CommissionRatePort.class),
                beans.getBeanProvider(IDebtUseCase.class));
        when(deliveryQuery.findAnnouncementById(any(), any())).thenReturn(Mono.empty());
        when(escrows.applyGuarantee(any(), any(), any())).thenReturn(Mono.empty());
        when(tenants.currentTenantId()).thenReturn(Mono.just(TENANT));
        when(open.clientId()).thenReturn(CLIENT);
        when(open.currency()).thenReturn("XAF");
        when(open.createdDeliveryId()).thenReturn(null);
        when(open.responses()).thenReturn(List.of(new AnnouncementResponseSnapshot(RESPONSE, PERSON,
                new BigDecimal("3000"), "XAF", "PENDING", Instant.now())));
        when(announcements.findById(TENANT, ANNOUNCEMENT)).thenReturn(Mono.just(open));
        when(announcements.resolveEscrowAmount(TENANT, ANNOUNCEMENT, RESPONSE)).thenReturn(Mono.just(new BigDecimal("3000")));
        when(deliveryQuery.resolveActorIdForDeliveryPerson(TENANT, PERSON)).thenReturn(Mono.just(COURIER));
        Announcement gofp = new Announcement();
        gofp.setPaymentMethod("ORANGE_MONEY");
        when(gofpAnnouncements.findById(ANNOUNCEMENT)).thenReturn(Mono.just(gofp));
    }

    private static PaymentIntent intent(UUID id, UUID delivery, PaymentIntent.Mode mode) {
        return new PaymentIntent(id, TENANT, delivery, ANNOUNCEMENT, RESPONSE, mode, new BigDecimal("3000"), "XAF",
                new EscrowShares(new BigDecimal("3000"), new BigDecimal("300"), List.of(), new BigDecimal("2700")),
                CLIENT, UUID.randomUUID(), COURIER, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plusSeconds(600), null, Instant.now());
    }

    @Test
    @DisplayName("challenge: a fresh delivery id, the response's price, the courier's actor and the commission rate go to the intent")
    void challenge() {
        when(escrows.createIntent(any())).thenAnswer(inv -> {
            IEscrowUseCase.IntentRequest r = inv.getArgument(0);
            return Mono.just(new IEscrowUseCase.IntentChallenge(intent(UUID.randomUUID(), r.deliveryId(), r.mode()),
                    "chal", 300L, "***1"));
        });
        var res = service.challenge(ANNOUNCEMENT, RESPONSE, CLIENT, null).block();

        ArgumentCaptor<IEscrowUseCase.IntentRequest> sent = ArgumentCaptor.forClass(IEscrowUseCase.IntentRequest.class);
        verify(escrows).createIntent(sent.capture());
        IEscrowUseCase.IntentRequest r = sent.getValue();
        assertThat(r.mode()).isEqualTo(PaymentIntent.Mode.ESCROW);
        assertThat(r.price()).isEqualByComparingTo("3000");
        assertThat(r.platformRate()).isEqualByComparingTo("0.10");
        assertThat(r.payerActorId()).isEqualTo(CLIENT);
        assertThat(r.courierActorId()).isEqualTo(COURIER);
        assertThat(r.deliveryId()).isNotNull();
        assertThat(res.deliveryId()).isEqualTo(r.deliveryId());
        assertThat(res.challengeToken()).isEqualTo("chal");
        assertThat(res.platformShare()).isEqualByComparingTo("300");
    }

    @Test
    @DisplayName("challenge refused to someone else than the client (403) and on an announcement already elected (409)")
    void challengeRefusals() {
        StepVerifier.create(service.challenge(ANNOUNCEMENT, RESPONSE, UUID.randomUUID(), null))
                .expectErrorMatches(e -> e instanceof WalletSettlementException w && w.getHttpStatus() == 403)
                .verify();
        when(open.createdDeliveryId()).thenReturn(UUID.randomUUID());
        StepVerifier.create(service.challenge(ANNOUNCEMENT, RESPONSE, CLIENT, null))
                .expectErrorMatches(e -> e instanceof WalletSettlementException w && w.getHttpStatus() == 409
                        && w.getCode().equals("ALREADY_SELECTED"))
                .verify();
        verify(escrows, never()).createIntent(any());
    }

    @Test
    @DisplayName("confirm: the escrow is held with the client's code, then the response elected with the escrowed delivery id")
    void confirmHoldsThenElects() {
        UUID intentId = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        when(escrows.intent(intentId)).thenReturn(Mono.just(intent(intentId, delivery, PaymentIntent.Mode.ESCROW)));
        when(escrows.hold(intentId, CLIENT, "chal", "123456")).thenReturn(Mono.just(mirror(delivery,
                EscrowMirror.Status.HELD)));
        when(announcementUseCase.assignResponse(ANNOUNCEMENT, CLIENT, RESPONSE, delivery))
                .thenReturn(Mono.just(mock(AnnouncementResponseDTO.class)));

        var res = service.confirm(ANNOUNCEMENT, RESPONSE, CLIENT, intentId, "chal", "123456").block();
        assertThat(res.deliveryId()).isEqualTo(delivery);
        assertThat(res.paymentStatus()).isEqualTo("HELD");
        verify(escrows, never()).refundAbortedSelection(any());
    }

    @Test
    @DisplayName("the election fails before the delivery exists: the escrow is refunded and the error returned")
    void electionFailureRefunds() {
        UUID intentId = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        when(escrows.intent(intentId)).thenReturn(Mono.just(intent(intentId, delivery, PaymentIntent.Mode.ESCROW)));
        when(escrows.hold(any(), any(), any(), any())).thenReturn(Mono.just(mirror(delivery, EscrowMirror.Status.HELD)));
        when(announcementUseCase.assignResponse(ANNOUNCEMENT, CLIENT, RESPONSE, delivery))
                .thenReturn(Mono.error(new IllegalStateException("courier not approved")));
        when(escrows.refundAbortedSelection(delivery)).thenReturn(Mono.just(mirror(delivery, EscrowMirror.Status.REFUNDED)));

        StepVerifier.create(service.confirm(ANNOUNCEMENT, RESPONSE, CLIENT, intentId, "chal", "123456"))
                .expectErrorMessage("courier not approved").verify();
        verify(escrows).refundAbortedSelection(delivery);
    }

    @Test
    @DisplayName("the election created the delivery but its follow-up failed: the payment is kept (a retry replays)")
    void followUpFailureKeepsPayment() {
        UUID intentId = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        when(escrows.intent(intentId)).thenReturn(Mono.just(intent(intentId, delivery, PaymentIntent.Mode.ESCROW)));
        when(escrows.hold(any(), any(), any(), any())).thenReturn(Mono.just(mirror(delivery, EscrowMirror.Status.HELD)));
        when(announcementUseCase.assignResponse(ANNOUNCEMENT, CLIENT, RESPONSE, delivery))
                .thenReturn(Mono.error(new IllegalStateException("price not recorded")));
        AnnouncementSnapshot elected = mock(AnnouncementSnapshot.class);
        when(elected.createdDeliveryId()).thenReturn(delivery);
        when(announcements.findById(TENANT, ANNOUNCEMENT)).thenReturn(Mono.just(elected));

        StepVerifier.create(service.confirm(ANNOUNCEMENT, RESPONSE, CLIENT, intentId, "chal", "123456"))
                .expectErrorMessage("price not recorded").verify();
        verify(escrows, never()).refundAbortedSelection(any());
    }

    @Test
    @DisplayName("CASH: the confirmation opens the cash collection (no code), cancelled if the election fails")
    void cashSelection() {
        UUID intentId = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        when(escrows.intent(intentId)).thenReturn(Mono.just(intent(intentId, delivery, PaymentIntent.Mode.CASH)));
        CashCollectionMirror opened = new CashCollectionMirror(UUID.randomUUID(), TENANT, delivery, UUID.randomUUID(),
                COURIER, UUID.randomUUID(), new BigDecimal("3000"), null, null, "XAF",
                CashCollectionMirror.Status.OPEN, Instant.now(), Instant.now());
        when(cash.open(intentId, CLIENT)).thenReturn(Mono.just(opened));
        when(announcementUseCase.assignResponse(ANNOUNCEMENT, CLIENT, RESPONSE, delivery))
                .thenReturn(Mono.error(new IllegalStateException("boom")));
        when(cash.cancel(delivery)).thenReturn(Mono.just(opened));

        StepVerifier.create(service.confirm(ANNOUNCEMENT, RESPONSE, CLIENT, intentId, null, null))
                .expectErrorMessage("boom").verify();
        verify(cash).cancel(delivery);
        verify(escrows, never()).hold(any(), any(), any(), any());
    }

    @Test
    @DisplayName("an intent of another response is refused (409) before any money moves")
    void foreignIntent() {
        UUID intentId = UUID.randomUUID();
        when(escrows.intent(intentId)).thenReturn(Mono.just(intent(intentId, UUID.randomUUID(),
                PaymentIntent.Mode.ESCROW)));
        StepVerifier.create(service.confirm(ANNOUNCEMENT, UUID.randomUUID(), CLIENT, intentId, "c", "1"))
                .expectErrorMatches(e -> e instanceof WalletSettlementException w && w.getHttpStatus() == 409)
                .verify();
        verify(escrows, never()).hold(any(), any(), any(), any());
        verify(announcementUseCase, never()).assignResponse(any(), any(), any(), eq((UUID) null));
    }

    @Test
    @DisplayName("QO-22: a return at the courier's expense is paid by the fund — FUND intent, no code, the courier at fault gets a PENDING debt at the election")
    void returnAtTheCouriersExpense() {
        UUID parent = UUID.randomUUID();
        UUID atFaultPerson = UUID.randomUUID();
        UUID atFault = UUID.randomUUID();
        returnOf(parent, ResponsibleParty.COURIER, atFaultPerson);
        when(deliveryQuery.resolveActorIdForDeliveryPerson(TENANT, atFaultPerson)).thenReturn(Mono.just(atFault));
        when(escrows.createIntent(any())).thenAnswer(inv -> {
            IEscrowUseCase.IntentRequest r = inv.getArgument(0);
            return Mono.just(new IEscrowUseCase.IntentChallenge(intent(UUID.randomUUID(), r.deliveryId(), r.mode()),
                    null, null, null));
        });

        var res = service.challenge(ANNOUNCEMENT, RESPONSE, CLIENT, "ESCROW").block();
        assertThat(res.paymentMode()).isEqualTo("FUND");
        assertThat(res.challengeToken()).isNull();

        UUID intentId = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        PaymentIntent fund = intent(intentId, delivery, PaymentIntent.Mode.FUND);
        when(escrows.intent(intentId)).thenReturn(Mono.just(fund));
        when(escrows.confirmFunded(intentId, CLIENT)).thenReturn(Mono.just(fund));
        when(announcementUseCase.assignResponse(ANNOUNCEMENT, CLIENT, RESPONSE, delivery))
                .thenReturn(Mono.just(mock(AnnouncementResponseDTO.class)));
        when(debts.recordPending(any())).thenAnswer(inv -> Mono.just(mock(WalletDebt.class)));

        var confirmed = service.confirm(ANNOUNCEMENT, RESPONSE, CLIENT, intentId, null, null).block();
        assertThat(confirmed.paymentStatus()).isEqualTo("FUNDED");
        verify(escrows, never()).hold(any(), any(), any(), any());
        ArgumentCaptor<IDebtUseCase.NewDebt> debt = ArgumentCaptor.forClass(IDebtUseCase.NewDebt.class);
        verify(debts).recordPending(debt.capture());
        assertThat(debt.getValue().debtorId()).isEqualTo(atFault);
        assertThat(debt.getValue().reason()).isEqualTo(WalletDebt.Reason.RETURN_COST);
        assertThat(debt.getValue().reference()).isEqualTo("RETURN:" + delivery);
        assertThat(debt.getValue().amount()).isEqualByComparingTo("2700");
    }

    @Test
    @DisplayName("a return at the platform's expense is paid by the fund without any debt; one at the sender's expense stays an escrow")
    void returnAtThePlatformsOrSendersExpense() {
        when(escrows.createIntent(any())).thenAnswer(inv -> {
            IEscrowUseCase.IntentRequest r = inv.getArgument(0);
            return Mono.just(new IEscrowUseCase.IntentChallenge(intent(UUID.randomUUID(), r.deliveryId(), r.mode()),
                    "chal", 300L, null));
        });
        returnOf(UUID.randomUUID(), ResponsibleParty.PLATFORM, UUID.randomUUID());
        assertThat(service.challenge(ANNOUNCEMENT, RESPONSE, CLIENT, null).block().paymentMode()).isEqualTo("FUND");

        returnOf(UUID.randomUUID(), ResponsibleParty.SENDER, UUID.randomUUID());
        assertThat(service.challenge(ANNOUNCEMENT, RESPONSE, CLIENT, null).block().paymentMode()).isEqualTo("ESCROW");

        StepVerifier.create(service.challenge(ANNOUNCEMENT, RESPONSE, CLIENT, "FUND"))
                .expectErrorMatches(e -> e instanceof WalletSettlementException w
                        && w.getCode().equals("PAYMENT_MODE_INVALID")).verify();
    }

    @Test
    @DisplayName("D-152: a delivery to a relay hub — two codes: the challenge returns the guarantee's, the confirmation holds the guarantee then the price")
    void relayDeliveryHoldsAGuarantee() {
        UUID hub = UUID.randomUUID();
        DeliveryAnnouncement toRelay = mock(DeliveryAnnouncement.class);
        when(toRelay.getKind()).thenReturn(AnnouncementKind.OUTBOUND);
        when(toRelay.getDetails()).thenReturn(new AnnouncementDetails(null, null, null, null, null, hub, null, null,
                null, null));
        when(deliveryQuery.findAnnouncementById(TENANT, ANNOUNCEMENT)).thenReturn(Mono.just(toRelay));
        when(escrows.createIntent(any())).thenAnswer(inv -> {
            IEscrowUseCase.IntentRequest r = inv.getArgument(0);
            return Mono.just(new IEscrowUseCase.IntentChallenge(intent(UUID.randomUUID(), r.deliveryId(), r.mode()),
                    "chal", 300L, "***1"));
        });
        when(escrows.guaranteeChallenge(any(), eq(CLIENT))).thenReturn(Mono.just(
                new com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelSettlementDtos.Challenge(
                        "gchal", 300L, "***2")));
        var ch = service.challenge(ANNOUNCEMENT, RESPONSE, CLIENT, null).block();
        assertThat(ch.guaranteeChallengeToken()).isEqualTo("gchal");
        assertThat(ch.guaranteeAmount()).isEqualByComparingTo("3000");

        UUID intentId = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        when(escrows.intent(intentId)).thenReturn(Mono.just(intent(intentId, delivery, PaymentIntent.Mode.ESCROW)));
        // without the second code: refused before anything is held
        StepVerifier.create(service.confirm(ANNOUNCEMENT, RESPONSE, CLIENT, intentId, "chal", "1"))
                .expectErrorMatches(e -> e instanceof WalletSettlementException w
                        && w.getCode().equals("RETURN_GUARANTEE_CODE_REQUIRED") && w.getHttpStatus() == 400).verify();
        verify(escrows, never()).hold(any(), any(), any(), any());

        EscrowMirror guarantee = new EscrowMirror(UUID.randomUUID(), TENANT, delivery, EscrowMirror.RETURN_GUARANTEE,
                UUID.randomUUID(), EscrowMirror.Status.HELD, new BigDecimal("3000"), "XAF", null, null, Instant.now(),
                Instant.now());
        when(escrows.holdGuarantee(intentId, CLIENT, "gchal", "2")).thenReturn(Mono.just(guarantee));
        when(escrows.applyGuarantee(eq(delivery), any(), any())).thenReturn(Mono.just(guarantee));
        // the price's code is wrong: the guarantee already held is refunded
        when(escrows.hold(intentId, CLIENT, "chal", "bad")).thenReturn(Mono.error(new WalletSettlementException(
                "KERNEL_REFUSED", 409, "bad code")));
        StepVerifier.create(service.confirm(ANNOUNCEMENT, RESPONSE, CLIENT, intentId, "chal", "bad", "gchal", "2"))
                .expectError(WalletSettlementException.class).verify();
        verify(escrows).applyGuarantee(delivery,
                com.yowyob.tiibntick.core.billing.wallet.domain.policy.EscrowOutcomePolicy.Action.REFUND,
                "SELECTION_ABORTED");

        when(escrows.hold(intentId, CLIENT, "chal", "1")).thenReturn(Mono.just(mirror(delivery,
                EscrowMirror.Status.HELD)));
        when(announcementUseCase.assignResponse(ANNOUNCEMENT, CLIENT, RESPONSE, delivery))
                .thenReturn(Mono.just(mock(AnnouncementResponseDTO.class)));
        var res = service.confirm(ANNOUNCEMENT, RESPONSE, CLIENT, intentId, "chal", "1", "gchal", "2").block();
        assertThat(res.paymentStatus()).isEqualTo("HELD");
        assertThat(res.guaranteeStatus()).isEqualTo("HELD");
    }

    @Test
    @DisplayName("D-151: the return of a parcel whose guarantee was captured (not collected at the relay) is paid by the fund, no debt")
    void returnFundedByTheCapturedGuarantee() {
        returnOf(UUID.randomUUID(), ResponsibleParty.SENDER, UUID.randomUUID());
        when(deliveryQuery.findDeliveryById(any(), any())).thenAnswer(inv -> {
            Delivery parent = mock(Delivery.class);
            when(parent.getReturnGuaranteeStatus()).thenReturn(
                    com.yowyob.tiibntick.core.delivery.domain.model.enums.ReturnGuaranteeStatus.CAPTURED);
            return Mono.just(parent);
        });
        when(escrows.createIntent(any())).thenAnswer(inv -> {
            IEscrowUseCase.IntentRequest r = inv.getArgument(0);
            return Mono.just(new IEscrowUseCase.IntentChallenge(intent(UUID.randomUUID(), r.deliveryId(), r.mode()),
                    null, null, null));
        });
        var ch = service.challenge(ANNOUNCEMENT, RESPONSE, CLIENT, null).block();
        assertThat(ch.paymentMode()).isEqualTo("FUND");
        assertThat(ch.guaranteeChallengeToken()).isNull();

        UUID intentId = UUID.randomUUID();
        UUID delivery = UUID.randomUUID();
        PaymentIntent fund = intent(intentId, delivery, PaymentIntent.Mode.FUND);
        when(escrows.intent(intentId)).thenReturn(Mono.just(fund));
        when(escrows.confirmFunded(intentId, CLIENT)).thenReturn(Mono.just(fund));
        when(announcementUseCase.assignResponse(ANNOUNCEMENT, CLIENT, RESPONSE, delivery))
                .thenReturn(Mono.just(mock(AnnouncementResponseDTO.class)));
        assertThat(service.confirm(ANNOUNCEMENT, RESPONSE, CLIENT, intentId, null, null).block().paymentStatus())
                .isEqualTo("FUNDED");
        verifyNoInteractions(debts);
    }

    private void returnOf(UUID parentId, ResponsibleParty payer, UUID parentCourierPerson) {
        DeliveryAnnouncement ret = mock(DeliveryAnnouncement.class);
        when(ret.getKind()).thenReturn(AnnouncementKind.RETURN);
        when(ret.getDetails()).thenReturn(new AnnouncementDetails(null, null, null, null, null, null, null, null,
                "RETURN_OF_DELIVERY", parentId));
        when(deliveryQuery.findAnnouncementById(TENANT, ANNOUNCEMENT)).thenReturn(Mono.just(ret));
        Delivery parent = mock(Delivery.class);
        Parcel parcel = mock(Parcel.class);
        when(parcel.getReturnCostPayer()).thenReturn(payer);
        when(parent.getParcel()).thenReturn(parcel);
        when(parent.getDeliveryPersonId()).thenReturn(parentCourierPerson);
        when(deliveryQuery.findDeliveryById(TENANT, parentId)).thenReturn(Mono.just(parent));
    }

    private static EscrowMirror mirror(UUID delivery, EscrowMirror.Status status) {
        return new EscrowMirror(UUID.randomUUID(), TENANT, delivery, EscrowMirror.DELIVERY, UUID.randomUUID(), status,
                new BigDecimal("3000"), "XAF", UUID.randomUUID(), null, Instant.now(), Instant.now());
    }
}
