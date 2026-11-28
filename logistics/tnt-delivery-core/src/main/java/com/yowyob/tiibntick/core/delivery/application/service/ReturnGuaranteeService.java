package com.yowyob.tiibntick.core.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryAnnouncementRepository;
import com.yowyob.tiibntick.core.delivery.application.port.out.ReturnGuaranteePort;
import com.yowyob.tiibntick.core.delivery.domain.event.AggregateEvent;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.Delivery;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryKind;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.ReturnGuaranteeStatus;
import com.yowyob.tiibntick.core.delivery.domain.statemachine.DeliveryStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * When to freeze, release or debit the return guarantee of a delivery to a relay hub (decisions D-147, D-151, admin
 * spec §5.2.1). The wallet operation itself is the {@link ReturnGuaranteePort} (Kernel escrow, lot L5).
 *
 * <ul>
 *   <li>an OUTBOUND delivery whose destination is a relay hub, once priced (agreed amount): freeze — only when the
 *       sender pays by wallet; a cash payment, an unknown payment method or amount are recorded {@code NOT_HANDLED};</li>
 *   <li>end of the relay storage (row D12): debit, the return being paid by the sender; without a frozen guarantee
 *       the case is recorded {@code NOT_HANDLED} (the return draft is opened anyway, never blocked);</li>
 *   <li>any other end of the delivery (collected at the relay, delivered, cancelled, failed): release.</li>
 * </ul>
 *
 * Every case not handled is logged with the marker {@code RETURN_GUARANTEE_NOT_HANDLED} and kept on the delivery
 * ({@code return_guarantee_status}, {@code return_guarantee_reason}) for the administrator.
 */
@Slf4j
@Service
public class ReturnGuaranteeService {

    /** The sender pays in cash (or another way than the wallet): D-147 "non traité pour le moment". */
    public static final String SENDER_NOT_PAYING_BY_WALLET = "SENDER_NOT_PAYING_BY_WALLET";
    /** No payment method recorded for the delivery (no announcement, or no method on it). */
    public static final String PAYMENT_METHOD_UNKNOWN = "PAYMENT_METHOD_UNKNOWN";
    /** The storage ended while no guarantee was frozen (delivery never priced, or a deposit at a relay not chosen). */
    public static final String NO_GUARANTEE_HELD = "NO_GUARANTEE_HELD";
    /** No implementation of the wallet operation yet (lot L5). */
    public static final String NOT_CONNECTED = "WALLET_GUARANTEE_NOT_CONNECTED";
    /** The wallet operation failed (error of the Kernel call). */
    public static final String WALLET_OPERATION_FAILED = "WALLET_OPERATION_FAILED";

    private final ObjectProvider<ReturnGuaranteePort> port;
    private final DeliveryAnnouncementRepository announcements;

    public ReturnGuaranteeService(ObjectProvider<ReturnGuaranteePort> port,
                                  DeliveryAnnouncementRepository announcements) {
        this.port = port;
        this.announcements = announcements;
    }

    /** @return whether D-147 applies: an OUTBOUND delivery whose destination is a relay hub */
    static boolean concerned(Delivery delivery) {
        return delivery.getKind() == DeliveryKind.OUTBOUND && delivery.getDestinationHubId() != null;
    }

    /**
     * Applies the guarantee effect of the transitions just recorded on the delivery (before it is saved): debit at
     * D12, release at any other end, otherwise a freeze when it is due.
     */
    public Mono<Delivery> onTransitions(Delivery delivery, List<AggregateEvent> transitions) {
        if (transitions.stream().anyMatch(e -> DeliveryStateMachine.D12.equals(e.row()))) {
            return captureAtStorageEnd(delivery);
        }
        if (transitions.stream().anyMatch(e -> isEnd(e.eventType()))) {
            return delivery.getReturnGuaranteeStatus() == ReturnGuaranteeStatus.HELD
                    ? call(delivery, null, ReturnGuaranteePort::release, "release")
                    : Mono.just(delivery);
        }
        return holdIfDue(delivery);
    }

    /**
     * Freezes the guarantee of a priced delivery to a relay hub, once. No-op when the delivery is not concerned, not
     * yet priced, or already has a guarantee state.
     */
    public Mono<Delivery> holdIfDue(Delivery delivery) {
        if (!concerned(delivery) || delivery.getReturnGuaranteeStatus() != null || delivery.getAgreedAmount() == null
                || delivery.getStatus().isTerminal()) {
            return Mono.just(delivery);
        }
        return paymentMethod(delivery).map(Optional::of).defaultIfEmpty(Optional.empty())
                .flatMap(method -> {
                    String m = method.map(String::strip).filter(s -> !s.isEmpty()).orElse(null);
                    if (m == null) {
                        return notHandled(delivery, PAYMENT_METHOD_UNKNOWN, "freeze");
                    }
                    if ("CASH".equalsIgnoreCase(m)) {
                        return notHandled(delivery, SENDER_NOT_PAYING_BY_WALLET, "freeze");
                    }
                    return call(delivery, m, ReturnGuaranteePort::hold, "freeze");
                });
    }

    private Mono<Delivery> captureAtStorageEnd(Delivery delivery) {
        ReturnGuaranteeStatus current = delivery.getReturnGuaranteeStatus();
        if (current == ReturnGuaranteeStatus.HELD) {
            return call(delivery, null, ReturnGuaranteePort::capture, "debit");
        }
        String reason = current == ReturnGuaranteeStatus.NOT_HANDLED || current == ReturnGuaranteeStatus.FAILED
                ? delivery.getReturnGuaranteeReason() : NO_GUARANTEE_HELD;
        return notHandled(delivery, reason, "debit");
    }

    private Mono<Delivery> call(Delivery delivery, String paymentMethod,
                                BiFunction<ReturnGuaranteePort, ReturnGuaranteePort.Request,
                                        Mono<ReturnGuaranteePort.Result>> operation, String what) {
        ReturnGuaranteePort wallet = port.getIfAvailable();
        if (wallet == null) {
            return notHandled(delivery, NOT_CONNECTED, what);
        }
        ReturnGuaranteePort.Request request = new ReturnGuaranteePort.Request(delivery.getTenantId(),
                delivery.getId(), delivery.getParcel() != null ? delivery.getParcel().getId() : null,
                delivery.getAnnouncementId(), delivery.getSenderId(),
                delivery.getDestinationHubId() != null ? delivery.getDestinationHubId() : delivery.getRelayHubId(),
                delivery.getAgreedAmount(), delivery.getAgreedCurrency(), paymentMethod);
        return operation.apply(wallet, request)
                .defaultIfEmpty(new ReturnGuaranteePort.Result(ReturnGuaranteeStatus.NOT_HANDLED, NOT_CONNECTED))
                .onErrorResume(e -> {
                    log.error("Return guarantee {} failed for delivery {}", what, delivery.getId(), e);
                    return Mono.just(new ReturnGuaranteePort.Result(ReturnGuaranteeStatus.FAILED,
                            WALLET_OPERATION_FAILED));
                })
                .map(result -> {
                    delivery.recordReturnGuarantee(result.status(), result.reason());
                    if (result.status() == ReturnGuaranteeStatus.NOT_HANDLED
                            || result.status() == ReturnGuaranteeStatus.FAILED) {
                        logNotHandled(delivery, result.reason(), what);
                    }
                    return delivery;
                });
    }

    private Mono<Delivery> notHandled(Delivery delivery, String reason, String what) {
        delivery.recordReturnGuarantee(ReturnGuaranteeStatus.NOT_HANDLED, reason);
        logNotHandled(delivery, reason, what);
        return Mono.just(delivery);
    }

    private static void logNotHandled(Delivery delivery, String reason, String what) {
        log.warn("RETURN_GUARANTEE_NOT_HANDLED delivery={} tenant={} operation={} reason={} — case left to the "
                        + "administrator (D-147, admin spec §5.2.1); the return is never blocked", delivery.getId(),
                delivery.getTenantId(), what, reason);
    }

    private Mono<String> paymentMethod(Delivery delivery) {
        if (delivery.getAnnouncementId() == null) {
            return Mono.empty();
        }
        return announcements.findById(delivery.getTenantId(), delivery.getAnnouncementId())
                .flatMap(a -> Mono.justOrEmpty(a.getDetails() != null ? a.getDetails().paymentMethod() : null));
    }

    private static boolean isEnd(String eventType) {
        return "DeliveryDelivered".equals(eventType) || "DeliveryFailed".equals(eventType)
                || "DeliveryCancelled".equals(eventType);
    }
}
