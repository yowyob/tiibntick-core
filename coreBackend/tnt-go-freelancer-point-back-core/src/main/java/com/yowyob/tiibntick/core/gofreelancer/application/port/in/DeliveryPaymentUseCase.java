package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.application.port.in.dto.DeliveryPaymentDtos;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Pays a delivery from the client's own Kernel wallet to the delivery person's Kernel wallet.
 *
 * <p>Flow (the platform never holds the money nor a ledger — the Kernel does):
 * <ol>
 *   <li>the assigned delivery person calls {@link #generateReceiveCode}: a short-lived Kernel
 *       receive code is generated with <em>their</em> token and stored on the delivery;</li>
 *   <li>the client calls {@link #startPayment}: the Core asks the Kernel for a transfer
 *       challenge towards that code (the Kernel sends a one-time confirmation code to the client);</li>
 *   <li>the client calls {@link #confirmPayment} with the challenge token and the code: the
 *       Kernel moves the money and the Core records the settlement.</li>
 * </ol>
 * All Kernel calls run with the caller's own bearer token.
 */
public interface DeliveryPaymentUseCase {

    Mono<DeliveryPaymentDtos.ReceiveCodeResponse> generateReceiveCode(UUID deliveryId, UUID callerUserId);

    Mono<DeliveryPaymentDtos.PaymentChallengeResponse> startPayment(UUID deliveryId, UUID callerUserId);

    Mono<DeliveryPaymentDtos.PaymentStatusResponse> confirmPayment(UUID deliveryId, UUID callerUserId,
                                                                   String challengeToken, String code);

    Mono<DeliveryPaymentDtos.PaymentStatusResponse> getPaymentStatus(UUID deliveryId, UUID callerUserId);
}
