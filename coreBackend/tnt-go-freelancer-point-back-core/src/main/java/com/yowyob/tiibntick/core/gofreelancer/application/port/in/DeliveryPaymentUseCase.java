package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Pays an elected delivery into a Kernel escrow (lot L5, spec §4.1; the paid selection is
 * {@code SelectionPaymentService}). The platform never holds the money nor a ledger — the Kernel does.
 * <ol>
 *   <li>the client calls {@link #startPayment}: a payment intent is created (split PLATFORM / COURIER) and the
 *       Kernel sends the client a one-time code;</li>
 *   <li>the client calls {@link #confirmPayment} with the challenge token and the code: the Kernel holds the
 *       escrow. It is released to the courier at the hand-over (or at once when the delivery is already
 *       delivered), and the settlement recorded then.</li>
 * </ol>
 * The receive-code transfer of the delivery person is gone (decision H.1).
 */
public interface DeliveryPaymentUseCase {

    Mono<DeliveryPaymentDtos.PaymentChallengeResponse> startPayment(UUID deliveryId, UUID callerUserId);

    Mono<DeliveryPaymentDtos.PaymentStatusResponse> confirmPayment(UUID deliveryId, UUID callerUserId,
                                                                   String challengeToken, String code);

    Mono<DeliveryPaymentDtos.PaymentStatusResponse> getPaymentStatus(UUID deliveryId, UUID callerUserId);
}
