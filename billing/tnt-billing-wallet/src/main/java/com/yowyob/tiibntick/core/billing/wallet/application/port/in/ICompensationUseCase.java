package com.yowyob.tiibntick.core.billing.wallet.application.port.in;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.command.PayCompensationCommand;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletCompensation;
import reactor.core.publisher.Mono;

/** Compensation fund of the platform (spec §4.3, lot L5). */
public interface ICompensationUseCase {

    /**
     * Idempotent per dispute: the mirror of a compensation already paid is returned without calling the Kernel.
     *
     * @return the mirror of the payment
     * @throws com.yowyob.tiibntick.core.billing.wallet.domain.exception.WalletSettlementException as an error signal:
     *         {@code COMPENSATION_FUND_INSUFFICIENT} (409), {@code BENEFICIARY_WALLET_MISSING} (409),
     *         {@code KERNEL_PAYMENT_REFUSED} (409), {@code KERNEL_PAYMENT_UNAVAILABLE} (503)
     */
    Mono<WalletCompensation> payDisputeCompensation(PayCompensationCommand command);

    /**
     * A return at the courier's or the platform's expense (QO-22, spec §4.4): the fund pays the return's courier.
     * Idempotent per return delivery (mirror key and Kernel reference {@code RETURN:<deliveryId>}); no
     * {@code compensation.paid} event (it is not a dispute). Same errors as {@link #payDisputeCompensation}.
     */
    Mono<WalletCompensation> payReturnCourier(java.util.UUID tenantId, java.util.UUID returnDeliveryId,
                                              java.util.UUID courierActorId, java.math.BigDecimal amount,
                                              String currency);

    /**
     * A replacement courier paid at the prorata of the distance (QO-11 / D-153): the escrow paid the whole courier part
     * to the first courier. Idempotent per delivery and rank (mirror key and Kernel reference
     * {@code REPLACEMENT:<deliveryId>:<index>}); {@code compensation.paid} with the reason {@code COURIER_REPLACEMENT}.
     * Same errors as {@link #payDisputeCompensation}.
     */
    Mono<WalletCompensation> payReplacementCourier(java.util.UUID tenantId, java.util.UUID deliveryId, int index,
                                                   java.util.UUID courierActorId, java.math.BigDecimal amount,
                                                   String currency);
}
