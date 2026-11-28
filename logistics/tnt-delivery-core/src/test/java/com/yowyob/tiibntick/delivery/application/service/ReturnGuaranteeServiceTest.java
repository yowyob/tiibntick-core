package com.yowyob.tiibntick.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryAnnouncementRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.ReturnGuaranteePort;
import com.yowyob.tiibntick.core.delivery.application.service.ReturnGuaranteeService;
import com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryAnnouncement;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Parcel;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ReturnGuaranteeStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.AnnouncementDetails;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.DeliveryAddress;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.EtaEstimate;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.GeoCoordinates;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RecipientInfo;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.RelayDeposit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-147, D-151: when the return guarantee of a delivery to a relay hub is frozen, released or debited. */
class ReturnGuaranteeServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID HUB = UUID.randomUUID();
    private static final UUID ANNOUNCEMENT = UUID.randomUUID();

    private final DeliveryAnnouncementRepository announcements = mock(DeliveryAnnouncementRepository.class);
    private final RecordingPort wallet = new RecordingPort();

    @Test
    @DisplayName("wallet payment: frozen once priced (amount = agreed amount), released when collected at the relay")
    void heldThenReleasedAtCollection() {
        paymentMethod("MOBILE_MONEY");
        Delivery d = pricedToRelay();
        service(wallet).holdIfDue(d).block();
        assertThat(d.getReturnGuaranteeStatus()).isEqualTo(ReturnGuaranteeStatus.HELD);
        assertThat(wallet.calls).containsExactly("hold");
        assertThat(wallet.last.amount()).isEqualByComparingTo("2500");
        assertThat(wallet.last.relayHubId()).isEqualTo(HUB);
        assertThat(wallet.last.senderId()).isEqualTo(SENDER);

        service(wallet).holdIfDue(d).block();
        assertThat(wallet.calls).as("frozen once").containsExactly("hold");

        atDestinationRelay(d);
        d.clearDomainEvents();
        d.complete(null);
        service(wallet).onTransitions(d, transitions(d)).block();
        assertThat(d.getReturnGuaranteeStatus()).isEqualTo(ReturnGuaranteeStatus.RELEASED);
        assertThat(wallet.calls).containsExactly("hold", "release");
    }

    @Test
    @DisplayName("wallet payment: debited at the end of the relay storage (D12), the sender pays the return")
    void heldThenCapturedAtStorageEnd() {
        paymentMethod("ORANGE_MONEY");
        Delivery d = pricedToRelay();
        service(wallet).holdIfDue(d).block();
        atDestinationRelay(d);
        d.clearDomainEvents();
        d.expireRelayStorage();
        service(wallet).onTransitions(d, transitions(d)).block();
        assertThat(d.getReturnGuaranteeStatus()).isEqualTo(ReturnGuaranteeStatus.CAPTURED);
        assertThat(d.getReturnGuaranteeReason()).isNull();
        assertThat(wallet.calls).containsExactly("hold", "capture");
    }

    @Test
    @DisplayName("cash or unknown payment method: not handled, explicit reason, no wallet call")
    void cashAndUnknownAreNotHandled() {
        paymentMethod("CASH");
        Delivery cash = pricedToRelay();
        service(wallet).holdIfDue(cash).block();
        assertThat(cash.getReturnGuaranteeStatus()).isEqualTo(ReturnGuaranteeStatus.NOT_HANDLED);
        assertThat(cash.getReturnGuaranteeReason()).isEqualTo(ReturnGuaranteeService.SENDER_NOT_PAYING_BY_WALLET);

        paymentMethod(null);
        Delivery unknown = pricedToRelay();
        service(wallet).holdIfDue(unknown).block();
        assertThat(unknown.getReturnGuaranteeReason()).isEqualTo(ReturnGuaranteeService.PAYMENT_METHOD_UNKNOWN);
        assertThat(wallet.calls).isEmpty();

        // the storage end keeps the case visible with its first reason, without any wallet call
        atDestinationRelay(cash);
        cash.clearDomainEvents();
        cash.expireRelayStorage();
        service(wallet).onTransitions(cash, transitions(cash)).block();
        assertThat(cash.getReturnGuaranteeStatus()).isEqualTo(ReturnGuaranteeStatus.NOT_HANDLED);
        assertThat(cash.getReturnGuaranteeReason()).isEqualTo(ReturnGuaranteeService.SENDER_NOT_PAYING_BY_WALLET);
        assertThat(wallet.calls).isEmpty();
    }

    @Test
    @DisplayName("no wallet operation connected (lot L5): not handled; a wallet error: FAILED")
    void notConnectedAndFailure() {
        paymentMethod("MOBILE_MONEY");
        Delivery d = pricedToRelay();
        service(null).holdIfDue(d).block();
        assertThat(d.getReturnGuaranteeStatus()).isEqualTo(ReturnGuaranteeStatus.NOT_HANDLED);
        assertThat(d.getReturnGuaranteeReason()).isEqualTo(ReturnGuaranteeService.NOT_CONNECTED);

        Delivery e = pricedToRelay();
        wallet.fail = true;
        service(wallet).holdIfDue(e).block();
        assertThat(e.getReturnGuaranteeStatus()).isEqualTo(ReturnGuaranteeStatus.FAILED);
        assertThat(e.getReturnGuaranteeReason()).isEqualTo(ReturnGuaranteeService.WALLET_OPERATION_FAILED);
    }

    @Test
    @DisplayName("not concerned (no destination relay, not priced): nothing; storage end without guarantee: flagged")
    void notConcernedAndNoGuaranteeHeld() {
        paymentMethod("MOBILE_MONEY");
        Delivery home = delivery();
        home.recordAgreedTerms(new BigDecimal("2000"), "XAF", null, null, null, null);
        service(wallet).holdIfDue(home).block();
        assertThat(home.getReturnGuaranteeStatus()).isNull();

        Delivery unpriced = delivery();
        unpriced.setDestinationHub(HUB);
        service(wallet).holdIfDue(unpriced).block();
        assertThat(unpriced.getReturnGuaranteeStatus()).isNull();
        atDestinationRelay(unpriced);
        unpriced.clearDomainEvents();
        unpriced.expireRelayStorage();
        service(wallet).onTransitions(unpriced, transitions(unpriced)).block();
        assertThat(unpriced.getReturnGuaranteeStatus()).isEqualTo(ReturnGuaranteeStatus.NOT_HANDLED);
        assertThat(unpriced.getReturnGuaranteeReason()).isEqualTo(ReturnGuaranteeService.NO_GUARANTEE_HELD);
        assertThat(wallet.calls).isEmpty();
        verify(announcements, never()).findById(TENANT, null);
    }

    @Test
    @DisplayName("a delivery cancelled while its guarantee is frozen releases it")
    void cancelledReleases() {
        paymentMethod("MOBILE_MONEY");
        Delivery d = pricedToRelay();
        service(wallet).holdIfDue(d).block();
        d.clearDomainEvents();
        d.cancel("no longer needed");
        service(wallet).onTransitions(d, transitions(d)).block();
        assertThat(d.getReturnGuaranteeStatus()).isEqualTo(ReturnGuaranteeStatus.RELEASED);
        assertThat(wallet.calls).containsExactly("hold", "release");
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────

    private ReturnGuaranteeService service(ReturnGuaranteePort port) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        if (port != null) {
            beans.addBean("wallet", port);
        }
        return new ReturnGuaranteeService(beans.getBeanProvider(ReturnGuaranteePort.class), announcements);
    }

    private void paymentMethod(String method) {
        DeliveryAnnouncement a = mock(DeliveryAnnouncement.class);
        when(a.getDetails()).thenReturn(new AnnouncementDetails(null, method, null, null, null, HUB, null, null,
                null, null));
        when(announcements.findById(any(), any())).thenReturn(Mono.just(a));
    }

    private static Delivery delivery() {
        Parcel parcel = Parcel.create(new PackageSpecification(1, 10, 10, 10, false, false, "Box"));
        Delivery d = Delivery.create(TENANT, ANNOUNCEMENT, SENDER, parcel, address(), address(),
                new RecipientInfo("Awa Ndiaye", "+237690000001", null), DeliveryUrgency.STANDARD, null, null);
        d.assignDeliveryPerson(UUID.randomUUID(), null, 3.0, null);
        return d;
    }

    private static Delivery pricedToRelay() {
        Delivery d = delivery();
        d.setDestinationHub(HUB);
        d.recordAgreedTerms(new BigDecimal("2500"), "XAF", null, null, null, null);
        return d;
    }

    private static void atDestinationRelay(Delivery d) {
        d.confirmPickup();
        d.startTransit(EtaEstimate.of(Instant.now().plusSeconds(600), 2.0, 10));
        d.depositAtRelayHub(RelayDeposit.at(HUB));
    }

    private static List<AggregateEvent> transitions(Delivery d) {
        return d.getAggregateEvents().stream().filter(AggregateEvent::isTransition).toList();
    }

    private static DeliveryAddress address() {
        return new DeliveryAddress(null, "Carrefour", "Bastos", "Yaoundé", "CM", new GeoCoordinates(3.88, 11.52));
    }

    /** Wallet double: records the operations, answers the matching state. */
    static final class RecordingPort implements ReturnGuaranteePort {
        final List<String> calls = new ArrayList<>();
        Request last;
        boolean fail;

        @Override
        public Mono<Result> hold(Request request) {
            return op("hold", request, ReturnGuaranteeStatus.HELD);
        }

        @Override
        public Mono<Result> release(Request request) {
            return op("release", request, ReturnGuaranteeStatus.RELEASED);
        }

        @Override
        public Mono<Result> capture(Request request) {
            return op("capture", request, ReturnGuaranteeStatus.CAPTURED);
        }

        private Mono<Result> op(String name, Request request, ReturnGuaranteeStatus status) {
            if (fail) {
                return Mono.error(new IllegalStateException("insufficient balance"));
            }
            calls.add(name);
            last = request;
            return Mono.just(new Result(status, null));
        }
    }
}
