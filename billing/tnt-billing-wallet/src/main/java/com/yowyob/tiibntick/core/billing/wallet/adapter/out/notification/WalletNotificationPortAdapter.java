package com.yowyob.tiibntick.core.billing.wallet.adapter.out.notification;

import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletNotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * WalletNotificationPortAdapter — delegates payment notifications to tnt-notify-core.
 * In the current implementation, this is a stub that logs the notification.
 * The full implementation will use the tnt-notify-core HTTP client.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
public class WalletNotificationPortAdapter implements IWalletNotificationPort {

    @Override
    public Mono<Void> sendPaymentConfirmed(UUID userId, String message) {
        log.info("NOTIFICATION [PAYMENT_CONFIRMED] userId={} message={}", userId, message);
        // TODO: Call tnt-notify-core REST API to send push/SMS
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendPaymentFailed(UUID userId, String message) {
        log.info("NOTIFICATION [PAYMENT_FAILED] userId={} message={}", userId, message);
        // TODO: Call tnt-notify-core REST API to send push/SMS
        return Mono.empty();
    }
}
