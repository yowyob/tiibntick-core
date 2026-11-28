package com.yowyob.tiibntick.core.delivery.application.port.out;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.ReturnGuaranteeStatus;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound port of the return guarantee of a delivery to a relay hub (decisions D-147, D-151): an extra amount frozen
 * on the sender's wallet, <b>exactly</b> the sum that pays the delivery to the relay with its shares (platform levy,
 * relay share when applicable, courier share…), released when the recipient collects the parcel, debited at the end
 * of the relay storage to pay the return. The relay never pays a return.
 *
 * <p>Implemented by the Kernel escrow lot (L5); delivery-core only decides <i>when</i> to call it
 * ({@code ReturnGuaranteeService}). Without an implementation, every case is recorded {@code NOT_HANDLED} with the
 * reason {@code WALLET_GUARANTEE_NOT_CONNECTED}. Each call is keyed by the delivery id and must be idempotent: it can
 * be repeated when the transaction of the delivery is retried.
 */
public interface ReturnGuaranteePort {

    /** Freezes {@code amount} on the sender's wallet. */
    Mono<Result> hold(Request request);

    /** Releases the frozen amount (parcel collected, or delivery ended without return). */
    Mono<Result> release(Request request);

    /** Debits the frozen amount for good: it pays the return of the parcel, the sender being the payer. */
    Mono<Result> capture(Request request);

    /**
     * @param amount        sum that paid the delivery to the relay (agreed amount), with its currency
     * @param paymentMethod how the sender pays, as recorded on the announcement (free label), may be {@code null}
     */
    record Request(UUID tenantId, UUID deliveryId, UUID parcelId, UUID announcementId, UUID senderId,
                   UUID relayHubId, BigDecimal amount, String currency, String paymentMethod) {
    }

    /**
     * @param status state after the operation ({@code HELD}, {@code RELEASED}, {@code CAPTURED}, {@code FAILED} or
     *               {@code NOT_HANDLED})
     * @param reason stable code when the operation did not succeed, {@code null} otherwise
     */
    record Result(ReturnGuaranteeStatus status, String reason) {
    }
}
