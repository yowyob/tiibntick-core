package com.yowyob.tiibntick.core.billing.wallet.application.port.out;

import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Secondary port — notification contract for payment confirmations.
 * Implemented by NotificationPortAdapter that calls tnt-notify-core.
 *
 * @author MANFOUO Braun
 */
public interface IWalletNotificationPort {

    /**
     * Sends a payment-confirmed push/SMS to the payer.
     *
     * @param userId  recipient user identifier
     * @param message notification body
     * @return void
     */
    Mono<Void> sendPaymentConfirmed(UUID userId, String message);

    /**
     * Sends a payment-failed notification.
     *
     * @param userId  recipient user identifier
     * @param message failure message
     * @return void
     */
    Mono<Void> sendPaymentFailed(UUID userId, String message);
}
